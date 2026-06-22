package com.printshop.quo.dto;

import java.math.BigDecimal;

/**
 * 报价 DTO。
 *
 * @see REQ-QUO-001
 */
public record Quotation(
        String quotationId,
        String orderId,
        BigDecimal discountRate,
        BigDecimal finalAmount,
        String approvalStatus
) {
}
