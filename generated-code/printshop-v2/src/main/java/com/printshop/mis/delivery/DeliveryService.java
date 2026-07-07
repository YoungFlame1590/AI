package com.printshop.mis.delivery;

import static com.printshop.mis.shared.MisSupport.asString;
import static com.printshop.mis.shared.MisSupport.code;
import static com.printshop.mis.shared.MisSupport.notFound;
import static com.printshop.mis.shared.MisSupport.now;
import static com.printshop.mis.shared.MisSupport.text;

import com.printshop.common.exception.BusinessException;
import com.printshop.mis.audit.AuditTrailService;
import com.printshop.mis.domain.DeliveryQuote;
import com.printshop.mis.domain.DeliveryTask;
import com.printshop.mis.domain.DeliveryTrackingEvent;
import com.printshop.mis.domain.PrintOrder;
import com.printshop.mis.domain.UserAccount;
import com.printshop.mis.feedback.FeedbackService;
import com.printshop.mis.identity.IdentityService;
import com.printshop.mis.order.OrderChangeGuard;
import com.printshop.mis.order.OrderService;
import com.printshop.mis.order.OrderStatusPolicy;
import com.printshop.mis.repository.DeliveryQuoteRepository;
import com.printshop.mis.repository.DeliveryTaskRepository;
import com.printshop.mis.repository.DeliveryTrackingEventRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DeliveryService {

    private final IdentityService identityService;
    private final OrderService orderService;
    private final OrderChangeGuard changeGuard;
    private final OrderStatusPolicy statusPolicy;
    private final DeliveryTaskRepository deliveryTasks;
    private final DeliveryQuoteRepository deliveryQuotes;
    private final DeliveryTrackingEventRepository trackingEvents;
    private final FeedbackService feedbackService;
    private final DeliveryChannelAdapter deliveryChannelAdapter;
    private final AuditTrailService audit;

    public DeliveryService(
            IdentityService identityService,
            OrderService orderService,
            OrderChangeGuard changeGuard,
            OrderStatusPolicy statusPolicy,
            DeliveryTaskRepository deliveryTasks,
            DeliveryQuoteRepository deliveryQuotes,
            DeliveryTrackingEventRepository trackingEvents,
            FeedbackService feedbackService,
            DeliveryChannelAdapter deliveryChannelAdapter,
            AuditTrailService audit
    ) {
        this.identityService = identityService;
        this.orderService = orderService;
        this.changeGuard = changeGuard;
        this.statusPolicy = statusPolicy;
        this.deliveryTasks = deliveryTasks;
        this.deliveryQuotes = deliveryQuotes;
        this.trackingEvents = trackingEvents;
        this.feedbackService = feedbackService;
        this.deliveryChannelAdapter = deliveryChannelAdapter;
        this.audit = audit;
    }

    public DeliveryQuote createDeliveryQuote(String username, Map<String, Object> payload) {
        Long orderId = Long.valueOf(String.valueOf(payload.get("orderId")));
        PrintOrder order = orderService.requireVisibleOrder(username, orderId);
        statusPolicy.requireStatus(order, java.util.Set.of(OrderStatusPolicy.PRODUCTION_DONE), "配送报价", "完成生产质检");
        changeGuard.requireNoPendingChange(order, "配送报价");
        String channelCode = text(asString(payload.get("channelCode")), "IMMEDIATE").toUpperCase();
        BigDecimal weight = new BigDecimal(String.valueOf(payload.getOrDefault("packageWeightKg", "1"))).max(new BigDecimal("0.1"));
        String pickupAddress = text(asString(payload.get("pickupAddress")), text(order.storeName, "门店"));
        String deliveryAddress = text(asString(payload.get("deliveryAddress")), "客户收货地址");
        var channelQuote = deliveryChannelAdapter.quote(
                channelCode,
                weight.setScale(2, RoundingMode.HALF_UP),
                pickupAddress,
                deliveryAddress
        );
        DeliveryQuote quote = new DeliveryQuote();
        quote.quoteNo = code("DLQ");
        quote.orderId = order.id;
        quote.channelCode = channelQuote.channelCode();
        quote.channelName = channelQuote.channelName();
        quote.pickupAddress = pickupAddress;
        quote.deliveryAddress = deliveryAddress;
        quote.packageWeightKg = weight.setScale(2, RoundingMode.HALF_UP);
        quote.estimatedMinutes = channelQuote.estimatedMinutes();
        quote.estimatedFee = channelQuote.fee();
        quote.status = "QUOTED";
        quote.createdAt = now();
        audit.record(username, "DLV", "CREATE_DELIVERY_QUOTE", "ORDER", order.id, quote.channelName + " " + quote.estimatedFee);
        return deliveryQuotes.save(quote);
    }

    @Transactional(readOnly = true)
    public List<DeliveryQuote> deliveryQuotes(String username) {
        return deliveryQuotes.findAll().stream()
                .filter(quote -> {
                    try {
                        orderService.requireVisibleOrder(username, quote.orderId);
                        return true;
                    } catch (RuntimeException ex) {
                        return false;
                    }
                })
                .toList();
    }

    public DeliveryTask confirmDeliveryQuote(String username, Long quoteId) {
        DeliveryQuote quote = deliveryQuotes.findById(quoteId).orElseThrow(() -> notFound("配送报价", quoteId));
        if (!"QUOTED".equals(quote.status)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "配送报价已确认或失效。");
        }
        DeliveryTask request = new DeliveryTask();
        request.orderId = quote.orderId;
        request.mode = quote.channelName;
        request.carrierName = "模拟" + quote.channelName;
        request.targetStore = quote.deliveryAddress;
        DeliveryTask task = createDeliveryTask(username, request);
        task.deliveryChannel = quote.channelCode;
        task.deliveryFee = quote.estimatedFee;
        task.estimatedMinutes = quote.estimatedMinutes;
        task.trackingNo = code("TRK");
        task.externalStatus = "CREATED";
        DeliveryTask saved = deliveryTasks.save(task);
        PrintOrder order = orderService.requireVisibleOrder(username, quote.orderId);
        order.totalAmount = order.totalAmount.add(quote.estimatedFee).setScale(2, RoundingMode.HALF_UP);
        order.currentStep = "第三方配送已下单：" + quote.channelName + "，运单号 " + saved.trackingNo;
        orderService.saveOrder(order);
        quote.status = "CONFIRMED";
        quote.confirmedAt = now();
        deliveryQuotes.save(quote);
        addTracking(saved, "CREATED", quote.pickupAddress, "第三方配送模拟下单成功。");
        audit.record(username, "DLV", "CONFIRM_DELIVERY_QUOTE", "DELIVERY_TASK", saved.id, saved.trackingNo);
        return saved;
    }

    public DeliveryTask createDeliveryTask(String username, DeliveryTask request) {
        PrintOrder order = orderService.requireVisibleOrder(username, request.orderId);
        statusPolicy.requireStatus(order, java.util.Set.of(OrderStatusPolicy.PRODUCTION_DONE), "生成配送", "完成生产质检");
        changeGuard.requireNoPendingChange(order, "生成配送");
        DeliveryTask task = new DeliveryTask();
        task.taskNo = text(request.taskNo, code("DLV"));
        task.orderId = request.orderId;
        task.mode = text(request.mode, "到店自提");
        task.carrierName = text(request.carrierName, "待分配");
        task.carrierUsername = identityService.resolveActiveCourierUsername(
                text(request.carrierUsername, task.carrierName)
        );
        task.targetStore = text(request.targetStore, "客户地址");
        task.status = "ASSIGNED";
        task.signedBy = request.signedBy;
        task.deliveryChannel = text(request.deliveryChannel, task.deliveryChannel);
        task.trackingNo = text(request.trackingNo, task.trackingNo);
        task.deliveryFee = request.deliveryFee == null ? task.deliveryFee : request.deliveryFee;
        task.externalStatus = text(request.externalStatus, task.externalStatus);
        task.estimatedMinutes = request.estimatedMinutes == null ? task.estimatedMinutes : request.estimatedMinutes;
        task.updatedAt = now();
        order.status = OrderStatusPolicy.DELIVERING;
        order.currentStep = "配送任务已生成，等待配送/外协人员接单或签收";
        order.updatedAt = now();
        orderService.saveOrder(order);
        audit.record(username, "DLV", "CREATE_DELIVERY_TASK", "DELIVERY_TASK", task.orderId, task.mode);
        return deliveryTasks.save(task);
    }

    @Transactional(readOnly = true)
    public List<DeliveryTask> deliveryTasks(String username) {
        UserAccount user = identityService.requireUser(username);
        if (!"COURIER".equals(user.role)) {
            return deliveryTasks.findAll().stream()
                    .filter(task -> canViewTask(user, task))
                    .toList();
        }
        return deliveryTasks.findAll().stream()
                .filter(task -> carrierMatches(user, task))
                .toList();
    }

    @Transactional(readOnly = true)
    public DeliveryTask getDeliveryTask(String username, Long id) {
        DeliveryTask task = deliveryTasks.findById(id).orElseThrow(() -> notFound("配送任务", id));
        orderService.requireVisibleOrder(username, task.orderId);
        return task;
    }

    public DeliveryTask updateDeliveryTask(String username, Long id, DeliveryTask request) {
        DeliveryTask task = getDeliveryTask(username, id);
        task.mode = text(request.mode, task.mode);
        if (request.carrierName != null && !request.carrierName.equals(task.carrierName)) {
            task.carrierName = text(request.carrierName, task.carrierName);
            task.carrierUsername = identityService.resolveActiveCourierUsername(task.carrierName);
        }
        if (request.carrierUsername != null) {
            task.carrierUsername = identityService.resolveActiveCourierUsername(request.carrierUsername);
        }
        task.targetStore = text(request.targetStore, task.targetStore);
        task.status = text(request.status, task.status);
        task.signedBy = text(request.signedBy, task.signedBy);
        task.deliveryChannel = text(request.deliveryChannel, task.deliveryChannel);
        task.trackingNo = text(request.trackingNo, task.trackingNo);
        task.deliveryFee = request.deliveryFee == null ? task.deliveryFee : request.deliveryFee;
        task.externalStatus = text(request.externalStatus, task.externalStatus);
        task.estimatedMinutes = request.estimatedMinutes == null ? task.estimatedMinutes : request.estimatedMinutes;
        task.updatedAt = now();
        audit.record(username, "DLV", "UPDATE_DELIVERY_TASK", "DELIVERY_TASK", id, task.status);
        return deliveryTasks.save(task);
    }

    public DeliveryTask signDelivery(String username, Long id, Map<String, Object> payload) {
        DeliveryTask task = getDeliveryTask(username, id);
        PrintOrder order = orderService.requireVisibleOrder(username, task.orderId);
        statusPolicy.requireStatus(order, java.util.Set.of(OrderStatusPolicy.DELIVERING), "签收", "生成配送并由配送员接单");
        task.status = "SIGNED";
        task.signedBy = text(asString(payload.get("signedBy")), identityService.requireUser(username).displayName);
        task.updatedAt = now();
        order.status = "DONE";
        order.currentStep = "订单已签收完成";
        order.updatedAt = now();
        orderService.saveOrder(order);
        feedbackService.ensureInvitation(username, order);
        audit.record(username, "DLV", "SIGN_DELIVERY", "DELIVERY_TASK", id, task.signedBy);
        return deliveryTasks.save(task);
    }

    public Map<String, Object> syncTracking(String username, Long id) {
        DeliveryTask task = getDeliveryTask(username, id);
        String nextStatus = deliveryChannelAdapter.nextStatus(text(task.externalStatus, "CREATED"));
        task.externalStatus = nextStatus;
        task.updatedAt = now();
        DeliveryTask saved = deliveryTasks.save(task);
        addTracking(saved, nextStatus, "模拟配送节点", deliveryChannelAdapter.trackingMessage(nextStatus));
        audit.record(username, "DLV", "SYNC_DELIVERY_TRACKING", "DELIVERY_TASK", id, nextStatus);
        return Map.of(
                "task", saved,
                "events", trackingEvents.findByDeliveryTaskIdOrderByOccurredAtDesc(id)
        );
    }

    public DeliveryTask acceptDeliveryByOrder(String username, Long orderId) {
        UserAccount user = identityService.requireUser(username);
        if (!"COURIER".equals(user.role)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只有配送/外协人员可以接受配送。");
        }
        PrintOrder order = orderService.requireVisibleOrder(username, orderId);
        statusPolicy.requireStatus(order, java.util.Set.of(OrderStatusPolicy.DELIVERING), "接受配送", "由运营生成配送任务");
        DeliveryTask task = deliveryTasks.findByOrderId(orderId).stream()
                .filter(this::canBeAccepted)
                .findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "该订单没有待接配送任务。"));
        task.carrierName = user.displayName;
        task.carrierUsername = user.username;
        task.status = "ACCEPTED";
        task.updatedAt = now();
        order.status = "DELIVERING";
        order.currentStep = "配送员已接单：" + user.displayName;
        order.updatedAt = now();
        orderService.saveOrder(order);
        audit.record(username, "DLV", "ACCEPT_DELIVERY", "DELIVERY_TASK", task.id, order.orderNo);
        return deliveryTasks.save(task);
    }

    public DeliveryTask deleteDeliveryTask(String username, Long id) {
        DeliveryTask task = getDeliveryTask(username, id);
        deliveryTasks.delete(task);
        audit.record(username, "DLV", "DELETE_DELIVERY_TASK", "DELIVERY_TASK", id, task.taskNo);
        return task;
    }

    @Transactional(readOnly = true)
    public List<DeliveryQuote> deliveryQuotes(String username, Long orderId) {
        orderService.requireVisibleOrder(username, orderId);
        return deliveryQuotes.findByOrderIdOrderByCreatedAtDesc(orderId);
    }

    @Transactional(readOnly = true)
    public List<DeliveryTrackingEvent> trackingEvents(String username, Long taskId) {
        getDeliveryTask(username, taskId);
        return trackingEvents.findByDeliveryTaskIdOrderByOccurredAtDesc(taskId);
    }

    private void addTracking(DeliveryTask task, String status, String location, String message) {
        DeliveryTrackingEvent event = new DeliveryTrackingEvent();
        event.deliveryTaskId = task.id;
        event.trackingNo = task.trackingNo;
        event.status = status;
        event.location = location;
        event.message = message;
        event.occurredAt = now();
        trackingEvents.save(event);
    }


    private boolean canBeAccepted(DeliveryTask task) {
        String carrier = text(task.carrierName, "");
        return carrier.isBlank() || "待分配".equals(carrier) || "PENDING".equals(task.status) || "ASSIGNED".equals(task.status);
    }

    private boolean carrierMatches(UserAccount user, DeliveryTask task) {
        String carrier = text(task.carrierName, "");
        String carrierUsername = text(task.carrierUsername, "");
        return carrierUsername.equals(user.username)
                || (carrierUsername.isBlank() && carrier.equals(user.username))
                || carrier.isBlank()
                || "待分配".equals(carrier)
                || "PENDING".equals(task.status);
    }

    private boolean canViewTask(UserAccount user, DeliveryTask task) {
        try {
            orderService.requireVisibleOrder(user.username, task.orderId);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
