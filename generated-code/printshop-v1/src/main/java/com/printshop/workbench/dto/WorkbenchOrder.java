package com.printshop.workbench.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 角色工作台中的订单视图。
 */
public record WorkbenchOrder(
        String orderId,
        String customerName,
        String storeName,
        String status,
        String quoteStatus,
        String productionStatus,
        String deliveryStatus,
        String invoiceStatus,
        String financeStatus,
        String priority,
        BigDecimal amount,
        String currentStep,
        List<String> tags
) {
}
