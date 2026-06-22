package com.printshop.fin.dto;

import java.math.BigDecimal;

/**
 * 发票 DTO。
 *
 * @see REQ-FIN-001
 */
public record Invoice(
        String invoiceId,
        String orderId,
        String invoiceStatus,
        BigDecimal amount,
        String triggerMode
) {
}
