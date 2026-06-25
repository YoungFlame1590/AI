package com.printshop.mis.order;

import static com.printshop.mis.shared.MisSupport.asString;
import static com.printshop.mis.shared.MisSupport.code;
import static com.printshop.mis.shared.MisSupport.forbidden;
import static com.printshop.mis.shared.MisSupport.now;
import static com.printshop.mis.shared.MisSupport.number;
import static com.printshop.mis.shared.MisSupport.text;

import com.printshop.common.exception.BusinessException;
import com.printshop.mis.audit.AuditTrailService;
import com.printshop.mis.domain.OrderChangeRequest;
import com.printshop.mis.domain.PrintOrder;
import com.printshop.mis.domain.UserAccount;
import com.printshop.mis.identity.IdentityService;
import com.printshop.mis.repository.OrderChangeRequestRepository;
import com.printshop.mis.repository.PrintOrderRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrderChangeRequestService {

    private static final Set<String> CHANGEABLE_ROLES = Set.of("CUSTOMER", "CLERK", "MANAGER", "ADMIN");
    private static final Set<String> APPROVER_ROLES = Set.of("MANAGER", "ADMIN");

    private final IdentityService identityService;
    private final OrderService orderService;
    private final PrintOrderRepository orders;
    private final OrderChangeRequestRepository changeRequests;
    private final OrderPricingPolicy pricing;
    private final OrderChangeGuard changeGuard;
    private final AuditTrailService audit;

    public OrderChangeRequestService(
            IdentityService identityService,
            OrderService orderService,
            PrintOrderRepository orders,
            OrderChangeRequestRepository changeRequests,
            OrderPricingPolicy pricing,
            OrderChangeGuard changeGuard,
            AuditTrailService audit
    ) {
        this.identityService = identityService;
        this.orderService = orderService;
        this.orders = orders;
        this.changeRequests = changeRequests;
        this.pricing = pricing;
        this.changeGuard = changeGuard;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<OrderChangeRequest> listChangeRequests(String username) {
        UserAccount user = identityService.requireUser(username);
        if (Set.of("MANAGER", "OPS", "FINANCE", "ADMIN").contains(user.role)) {
            return changeRequests.findAll().stream()
                    .sorted(Comparator.comparing((OrderChangeRequest item) -> item.createdAt).reversed())
                    .toList();
        }
        return orderService.visibleOrders(user).stream()
                .flatMap(order -> changeRequests.findByOrderIdOrderByCreatedAtDesc(order.id).stream())
                .sorted(Comparator.comparing((OrderChangeRequest item) -> item.createdAt).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderChangeRequest getChangeRequest(String username, Long id) {
        OrderChangeRequest request = changeRequests.findById(id)
                .orElseThrow(() -> com.printshop.mis.shared.MisSupport.notFound("订单变更请求", id));
        orderService.requireVisibleOrder(username, request.orderId);
        return request;
    }

    public OrderChangeRequest createChangeRequest(String username, Long orderId, Map<String, Object> payload) {
        UserAccount user = identityService.requireUser(username);
        if (!CHANGEABLE_ROLES.contains(user.role)) {
            throw forbidden("当前角色不能发起订单变更。");
        }
        PrintOrder order = orderService.requireVisibleOrder(username, orderId);
        if (Set.of(OrderStatusPolicy.DONE, OrderStatusPolicy.REFUNDED, OrderStatusPolicy.CANCELLED).contains(order.status)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "订单已结束，不能发起变更。");
        }
        if (changeGuard.hasPendingChange(order.id)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该订单已有待审批变更，请先处理后再提交新变更。");
        }

        PrintOrder proposal = proposalFrom(order, payload);
        OrderChangeRequest change = new OrderChangeRequest();
        change.requestNo = code("CRQ");
        change.orderId = order.id;
        change.orderNo = order.orderNo;
        change.requestedById = user.id;
        change.requestedBy = user.displayName;
        change.requesterRole = user.role;
        change.status = "PENDING";
        change.reason = text(asString(payload.get("reason")), "订单规格或交付要求变更");
        change.oldProductType = order.productType;
        change.newProductType = proposal.productType;
        change.oldColorMode = order.colorMode;
        change.newColorMode = proposal.colorMode;
        change.oldPageCount = order.pageCount;
        change.newPageCount = proposal.pageCount;
        change.oldCopies = order.copies;
        change.newCopies = proposal.copies;
        change.oldDeliveryMode = order.deliveryMode;
        change.newDeliveryMode = proposal.deliveryMode;
        change.oldPriority = order.priority;
        change.newPriority = proposal.priority;
        change.oldAmount = amount(order.totalAmount);
        change.newAmount = pricing.calculate(proposal);
        change.amountDelta = change.newAmount.subtract(change.oldAmount);
        change.freezeStartedAt = now();
        change.createdAt = change.freezeStartedAt;
        change.updatedAt = change.freezeStartedAt;

        order.currentStep = "存在待审批订单变更，生产/SLA 已冻结";
        order.updatedAt = now();
        orders.save(order);
        OrderChangeRequest saved = changeRequests.save(change);
        audit.record(username, "ORD", "CREATE_ORDER_CHANGE_REQUEST", "ORDER_CHANGE_REQUEST", saved.id, saved.requestNo);
        return saved;
    }

    public OrderChangeRequest approveChangeRequest(String username, Long id, Map<String, Object> payload) {
        UserAccount user = identityService.requireUser(username);
        requireApprover(user);
        OrderChangeRequest change = requirePending(id);
        PrintOrder order = orderService.requireVisibleOrder(username, change.orderId);
        order.productType = change.newProductType;
        order.colorMode = change.newColorMode;
        order.pageCount = change.newPageCount;
        order.copies = change.newCopies;
        order.deliveryMode = change.newDeliveryMode;
        order.priority = change.newPriority;
        order.totalAmount = change.newAmount;
        order.currentStep = "订单变更已批准，生产/SLA 已恢复";
        order.updatedAt = now();
        orders.save(order);

        change.status = "APPROVED";
        change.approvedBy = user.displayName;
        change.decisionComment = text(asString(payload.get("comment")), "同意订单变更");
        change.freezeEndedAt = now();
        change.updatedAt = change.freezeEndedAt;
        audit.record(username, "ORD", "APPROVE_ORDER_CHANGE_REQUEST", "ORDER_CHANGE_REQUEST", change.id, change.requestNo);
        return changeRequests.save(change);
    }

    public OrderChangeRequest rejectChangeRequest(String username, Long id, Map<String, Object> payload) {
        UserAccount user = identityService.requireUser(username);
        requireApprover(user);
        OrderChangeRequest change = requirePending(id);
        PrintOrder order = orderService.requireVisibleOrder(username, change.orderId);
        order.currentStep = "订单变更已驳回，按原订单继续处理";
        order.updatedAt = now();
        orders.save(order);

        change.status = "REJECTED";
        change.approvedBy = user.displayName;
        change.decisionComment = text(asString(payload.get("comment")), "驳回订单变更");
        change.freezeEndedAt = now();
        change.updatedAt = change.freezeEndedAt;
        audit.record(username, "ORD", "REJECT_ORDER_CHANGE_REQUEST", "ORDER_CHANGE_REQUEST", change.id, change.requestNo);
        return changeRequests.save(change);
    }

    private PrintOrder proposalFrom(PrintOrder order, Map<String, Object> payload) {
        PrintOrder proposal = new PrintOrder();
        proposal.productType = normalize(asString(payload.get("productType")), order.productType, OrderPricingPolicy.PRODUCT_TYPES, "产品类型");
        proposal.colorMode = normalize(asString(payload.get("colorMode")), order.colorMode, OrderPricingPolicy.COLOR_MODES, "颜色/工艺");
        proposal.pageCount = numberFrom(payload.get("pageCount"), order.pageCount);
        proposal.copies = numberFrom(payload.get("copies"), order.copies);
        pricing.validatePositive(proposal.pageCount, "页数");
        pricing.validatePositive(proposal.copies, "份数");
        proposal.deliveryMode = normalize(asString(payload.get("deliveryMode")), order.deliveryMode, OrderPricingPolicy.DELIVERY_MODES, "交付方式");
        proposal.priority = normalize(asString(payload.get("priority")), order.priority, OrderPricingPolicy.PRIORITIES, "优先级");
        return proposal;
    }

    private String normalize(String requested, String fallback, Set<String> options, String label) {
        return pricing.normalizeOption(text(requested, fallback), options, label);
    }

    private Integer numberFrom(Object value, Integer fallback) {
        if (value instanceof Number num) {
            return num.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return number(null, fallback);
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private OrderChangeRequest requirePending(Long id) {
        OrderChangeRequest change = changeRequests.findById(id)
                .orElseThrow(() -> com.printshop.mis.shared.MisSupport.notFound("订单变更请求", id));
        if (!"PENDING".equals(change.status)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "订单变更请求已处理，不能重复审批。");
        }
        return change;
    }

    private void requireApprover(UserAccount user) {
        if (!APPROVER_ROLES.contains(user.role)) {
            throw forbidden("只有门店店长或系统管理员可以审批订单变更。");
        }
    }
}
