package com.printshop.mis.order;

import com.printshop.common.exception.BusinessException;
import com.printshop.mis.domain.PrintOrder;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class OrderStatusPolicy {

    public static final String SUBMITTED = "SUBMITTED";
    public static final String REVIEWING = "REVIEWING";
    public static final String QUOTED = "QUOTED";
    public static final String JOB_READY = "JOB_READY";
    public static final String IN_PRODUCTION = "IN_PRODUCTION";
    public static final String PRODUCTION_DONE = "PRODUCTION_DONE";
    public static final String DELIVERING = "DELIVERING";
    public static final String DONE = "DONE";
    public static final String REFUNDED = "REFUNDED";
    public static final String CANCELLED = "CANCELLED";

    public void requireStatus(PrintOrder order, Set<String> allowed, String action, String hint) {
        if (!allowed.contains(order.status)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "不能执行“" + action + "”：当前订单状态为 " + order.status + "。请先" + hint + "。"
            );
        }
    }

    public void requirePaid(PrintOrder order, String action) {
        if (!Set.of("PAID", "PARTIAL").contains(order.paymentStatus)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "不能执行“" + action + "”：订单尚未收款。请先登记收款。"
            );
        }
    }

    public void assertManualTransition(PrintOrder order, String requestedStatus) {
        switch (requestedStatus) {
            case REVIEWING -> requireStatus(order, Set.of(SUBMITTED, REVIEWING), "提交审核", "提交订单");
            case IN_PRODUCTION -> requireStatus(order, Set.of(JOB_READY), "进入生产", "生成作业单");
            case DELIVERING -> requireStatus(order, Set.of(PRODUCTION_DONE), "进入配送", "完成生产质检");
            case DONE -> requireStatus(order, Set.of(DELIVERING), "完成订单", "完成配送签收");
            case CANCELLED -> {
                if (Set.of(DONE, REFUNDED).contains(order.status)) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "订单已结束，不能取消。");
                }
            }
            default -> throw new BusinessException(HttpStatus.BAD_REQUEST, "不支持手动流转到状态：" + requestedStatus);
        }
    }
}
