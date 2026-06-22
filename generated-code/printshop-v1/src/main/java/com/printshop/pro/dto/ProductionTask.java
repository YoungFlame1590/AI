package com.printshop.pro.dto;

import java.math.BigDecimal;

/**
 * 排产任务 DTO。
 *
 * @see REQ-PRO-001
 */
public record ProductionTask(
        String taskId,
        String orderId,
        String deviceSn,
        String slaDeadline,
        BigDecimal creditLimitUsed
) {
}
