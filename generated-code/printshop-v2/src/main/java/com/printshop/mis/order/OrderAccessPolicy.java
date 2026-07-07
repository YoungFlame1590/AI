package com.printshop.mis.order;

import static com.printshop.mis.shared.MisSupport.forbidden;
import static com.printshop.mis.shared.MisSupport.notFound;
import static com.printshop.mis.shared.MisSupport.text;

import com.printshop.mis.domain.DeliveryTask;
import com.printshop.mis.domain.PrintOrder;
import com.printshop.mis.domain.UserAccount;
import com.printshop.mis.repository.DeliveryTaskRepository;
import com.printshop.mis.repository.PrintOrderRepository;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class OrderAccessPolicy {

    static final Set<String> EARLY_ORDER_STATUSES = Set.of("SUBMITTED", "REVIEWING");

    private final PrintOrderRepository orders;
    private final DeliveryTaskRepository deliveryTasks;

    public OrderAccessPolicy(PrintOrderRepository orders, DeliveryTaskRepository deliveryTasks) {
        this.orders = orders;
        this.deliveryTasks = deliveryTasks;
    }

    public PrintOrder requireVisibleOrder(UserAccount user, Long id) {
        PrintOrder order = orders.findById(id).orElseThrow(() -> notFound("订单", id));
        if (!canViewOrder(user, order)) {
            throw notFound("订单", id);
        }
        return order;
    }

    public boolean canViewOrder(UserAccount user, PrintOrder order) {
        return switch (user.role) {
            case "CUSTOMER" -> Objects.equals(order.customerId, user.id);
            case "CLERK", "MANAGER" -> user.storeId != null && Objects.equals(order.storeId, user.storeId);
            case "OPS", "FINANCE", "ADMIN" -> true;
            case "COURIER" -> courierOrderIds(user).contains(order.id);
            default -> false;
        };
    }

    public boolean canChangeOrderStatus(UserAccount user) {
        return Set.of("CLERK", "MANAGER", "OPS", "COURIER", "ADMIN").contains(user.role);
    }

    public void assertCanChangeOrderStatus(UserAccount user) {
        if (!canChangeOrderStatus(user)) {
            throw forbidden("当前角色不能流转订单状态。");
        }
    }

    public void assertCanDeleteOrder(UserAccount user, PrintOrder order) {
        if ("ADMIN".equals(user.role) || "CLERK".equals(user.role) || "MANAGER".equals(user.role)) {
            return;
        }
        if ("CUSTOMER".equals(user.role) && EARLY_ORDER_STATUSES.contains(order.status)) {
            return;
        }
        throw forbidden("当前角色不能删除该订单。");
    }

    public Set<String> editableOrderFields(UserAccount user) {
        return switch (user.role) {
            case "CUSTOMER" -> Set.of("productType", "colorMode", "pageCount", "copies", "paperType", "craftType", "deliveryMode", "priority");
            case "CLERK", "MANAGER" -> Set.of("productType", "colorMode", "pageCount", "copies", "sizeName", "paperType", "craftType", "deliveryMode", "priority", "status", "currentStep");
            case "OPS" -> Set.of("deliveryMode", "priority", "status", "currentStep");
            case "FINANCE" -> Set.of("paymentStatus", "currentStep");
            case "COURIER" -> Set.of("status", "currentStep");
            case "ADMIN" -> Set.of("productType", "colorMode", "pageCount", "copies", "sizeName", "paperType", "craftType", "dueAt", "deliveryMode", "priority", "status", "paymentStatus", "currentStep");
            default -> Set.of();
        };
    }

    private Set<Long> courierOrderIds(UserAccount user) {
        return deliveryTasks.findAll().stream()
                .filter(task -> carrierMatches(user, task))
                .map(task -> task.orderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
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
}
