package com.printshop.mis.order;

import com.printshop.common.exception.BusinessException;
import com.printshop.mis.domain.PrintOrder;
import com.printshop.mis.repository.OrderChangeRequestRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class OrderChangeGuard {

    private final OrderChangeRequestRepository changeRequests;

    public OrderChangeGuard(OrderChangeRequestRepository changeRequests) {
        this.changeRequests = changeRequests;
    }

    public boolean hasPendingChange(Long orderId) {
        return !changeRequests.findByOrderIdAndStatusOrderByCreatedAtDesc(orderId, "PENDING").isEmpty();
    }

    public void requireNoPendingChange(PrintOrder order, String action) {
        if (order != null && order.id != null && hasPendingChange(order.id)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "不能执行“" + action + "”：订单存在待审批变更，生产/SLA 已冻结。请先由店长审批或驳回变更请求。"
            );
        }
    }
}
