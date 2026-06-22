package com.printshop.infra.state;

import org.springframework.stereotype.Component;

/**
 * 统一状态机接口，业务模块不得直接 setStatus。
 */
@Component
public class StateMachine {

    public String transit(String currentStatus, String event) {
        return switch (event) {
            case "ORDER_CREATED" -> "待质检";
            case "PAYMENT_CONFIRMED" -> "待排产";
            case "PRODUCTION_DISPATCHED" -> "生产中";
            case "DELIVERY_ROUTED" -> "配送中";
            case "INVOICE_ISSUED" -> "已开票";
            default -> currentStatus == null || currentStatus.isBlank() ? "已创建" : currentStatus;
        };
    }
}
