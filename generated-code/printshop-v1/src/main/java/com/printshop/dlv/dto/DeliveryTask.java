package com.printshop.dlv.dto;

import java.math.BigDecimal;

/**
 * 配送外协任务 DTO。
 *
 * @see REQ-DLV-001
 */
public record DeliveryTask(
        String taskId,
        String orderId,
        String targetStoreId,
        String financialVerifyStatus,
        BigDecimal outsourcingCostRatio
) {
}
