package com.printshop.mis.delivery;

import static com.printshop.mis.shared.MisSupport.asString;
import static com.printshop.mis.shared.MisSupport.code;
import static com.printshop.mis.shared.MisSupport.notFound;
import static com.printshop.mis.shared.MisSupport.now;
import static com.printshop.mis.shared.MisSupport.text;

import com.printshop.common.exception.BusinessException;
import com.printshop.mis.audit.AuditTrailService;
import com.printshop.mis.domain.DeliveryTask;
import com.printshop.mis.domain.PrintOrder;
import com.printshop.mis.domain.UserAccount;
import com.printshop.mis.identity.IdentityService;
import com.printshop.mis.order.OrderChangeGuard;
import com.printshop.mis.order.OrderService;
import com.printshop.mis.order.OrderStatusPolicy;
import com.printshop.mis.repository.DeliveryTaskRepository;
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
    private final AuditTrailService audit;

    public DeliveryService(IdentityService identityService, OrderService orderService, OrderChangeGuard changeGuard, OrderStatusPolicy statusPolicy, DeliveryTaskRepository deliveryTasks, AuditTrailService audit) {
        this.identityService = identityService;
        this.orderService = orderService;
        this.changeGuard = changeGuard;
        this.statusPolicy = statusPolicy;
        this.deliveryTasks = deliveryTasks;
        this.audit = audit;
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
        task.targetStore = text(request.targetStore, "客户地址");
        task.status = "ASSIGNED";
        task.signedBy = request.signedBy;
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
            return deliveryTasks.findAll();
        }
        return deliveryTasks.findAll().stream()
                .filter(task -> carrierMatches(user, task))
                .toList();
    }

    @Transactional(readOnly = true)
    public DeliveryTask getDeliveryTask(Long id) {
        return deliveryTasks.findById(id).orElseThrow(() -> notFound("配送任务", id));
    }

    public DeliveryTask updateDeliveryTask(String username, Long id, DeliveryTask request) {
        DeliveryTask task = getDeliveryTask(id);
        task.mode = text(request.mode, task.mode);
        task.carrierName = text(request.carrierName, task.carrierName);
        task.targetStore = text(request.targetStore, task.targetStore);
        task.status = text(request.status, task.status);
        task.signedBy = text(request.signedBy, task.signedBy);
        task.updatedAt = now();
        audit.record(username, "DLV", "UPDATE_DELIVERY_TASK", "DELIVERY_TASK", id, task.status);
        return deliveryTasks.save(task);
    }

    public DeliveryTask signDelivery(String username, Long id, Map<String, Object> payload) {
        DeliveryTask task = getDeliveryTask(id);
        PrintOrder order = orderService.requireVisibleOrder(username, task.orderId);
        statusPolicy.requireStatus(order, java.util.Set.of(OrderStatusPolicy.DELIVERING), "签收", "生成配送并由配送员接单");
        task.status = "SIGNED";
        task.signedBy = text(asString(payload.get("signedBy")), identityService.requireUser(username).displayName);
        task.updatedAt = now();
        order.status = "DONE";
        order.currentStep = "订单已签收完成";
        order.updatedAt = now();
        orderService.saveOrder(order);
        audit.record(username, "DLV", "SIGN_DELIVERY", "DELIVERY_TASK", id, task.signedBy);
        return deliveryTasks.save(task);
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
        DeliveryTask task = getDeliveryTask(id);
        deliveryTasks.delete(task);
        audit.record(username, "DLV", "DELETE_DELIVERY_TASK", "DELIVERY_TASK", id, task.taskNo);
        return task;
    }

    private boolean canBeAccepted(DeliveryTask task) {
        String carrier = text(task.carrierName, "");
        return carrier.isBlank() || "待分配".equals(carrier) || "PENDING".equals(task.status) || "ASSIGNED".equals(task.status);
    }

    private boolean carrierMatches(UserAccount user, DeliveryTask task) {
        String carrier = text(task.carrierName, "");
        return carrier.equals(user.displayName)
                || carrier.equals(user.username)
                || carrier.isBlank()
                || "待分配".equals(carrier)
                || "PENDING".equals(task.status);
    }
}
