package com.printshop.mis.order;

import com.printshop.common.exception.BusinessException;
import com.printshop.mis.domain.PrintOrder;
import com.printshop.mis.finance.FinanceService;
import com.printshop.mis.identity.IdentityService;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class OrderWorkflowPolicy {

    private final IdentityService identityService;
    private final OrderChangeGuard changeGuard;
    private final FinanceService financeService;

    public OrderWorkflowPolicy(IdentityService identityService, OrderChangeGuard changeGuard, FinanceService financeService) {
        this.identityService = identityService;
        this.changeGuard = changeGuard;
        this.financeService = financeService;
    }

    public boolean available(String username, PrintOrder order, String action) {
        String role = identityService.requireUser(username).role;
        boolean frozen = changeGuard.hasPendingChange(order.id);
        return switch (action) {
            case "SUBMIT_REVIEW" -> "CUSTOMER".equals(role) && OrderStatusPolicy.SUBMITTED.equals(order.status);
            case "REQUEST_CHANGE" -> Set.of("CUSTOMER", "CLERK", "ADMIN").contains(role)
                    && !Set.of(OrderStatusPolicy.DONE, OrderStatusPolicy.REFUNDED, OrderStatusPolicy.CANCELLED).contains(order.status)
                    && !frozen;
            case "QUOTE" -> Set.of("CLERK", "ADMIN").contains(role) && OrderStatusPolicy.REVIEWING.equals(order.status);
            case "JOB_TICKET" -> Set.of("CLERK", "ADMIN").contains(role) && OrderStatusPolicy.QUOTED.equals(order.status);
            case "SCHEDULE_PRODUCTION" -> Set.of("OPS", "ADMIN").contains(role)
                    && OrderStatusPolicy.JOB_READY.equals(order.status)
                    && !frozen;
            case "COMPLETE_PRODUCTION" -> Set.of("OPS", "ADMIN").contains(role)
                    && OrderStatusPolicy.IN_PRODUCTION.equals(order.status)
                    && !frozen;
            case "CREATE_DELIVERY" -> Set.of("OPS", "ADMIN").contains(role)
                    && OrderStatusPolicy.PRODUCTION_DONE.equals(order.status)
                    && !frozen;
            case "ACCEPT_DELIVERY", "SIGN_DELIVERY" -> "COURIER".equals(role)
                    && OrderStatusPolicy.DELIVERING.equals(order.status);
            case "PAY" -> Set.of("FINANCE", "ADMIN").contains(role) && canPay(order);
            case "INVOICE" -> Set.of("CUSTOMER", "FINANCE", "ADMIN").contains(role)
                    && "PAID".equals(order.paymentStatus)
                    && !financeService.hasRefundRecord(order.id)
                    && (("CUSTOMER".equals(role) && !financeService.hasActiveInvoice(order.id))
                    || (Set.of("FINANCE", "ADMIN").contains(role) && !financeService.hasIssuedInvoice(order.id)));
            case "REFUND" -> Set.of("CUSTOMER", "FINANCE", "ADMIN").contains(role)
                    && "PAID".equals(order.paymentStatus)
                    && (("CUSTOMER".equals(role) && !financeService.hasRefundRecord(order.id))
                    || (Set.of("FINANCE", "ADMIN").contains(role) && !financeService.hasRefundedPayment(order.id)));
            default -> false;
        };
    }

    public void requireAvailable(String username, PrintOrder order, String action) {
        if (!available(username, order, action)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "当前订单状态或角色不允许执行动作：" + action);
        }
    }

    public boolean canPay(PrintOrder order) {
        return Set.of(
                OrderStatusPolicy.QUOTED,
                OrderStatusPolicy.JOB_READY,
                OrderStatusPolicy.IN_PRODUCTION,
                OrderStatusPolicy.PRODUCTION_DONE,
                OrderStatusPolicy.DELIVERING,
                OrderStatusPolicy.DONE
        ).contains(order.status) && !"PAID".equals(order.paymentStatus) && !"REFUNDED".equals(order.paymentStatus);
    }
}
