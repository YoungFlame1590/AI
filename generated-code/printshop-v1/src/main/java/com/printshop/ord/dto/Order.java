package com.printshop.ord.dto;

import java.math.BigDecimal;

/**
 * 订单 DTO。
 *
 * @see REQ-ORD-001
 */
public record Order(
        String orderId,
        String orderStatus,
        BigDecimal fileSizeMb,
        Integer pageCount,
        String paymentStatus,
        String financialVerifyStatus
) {
}
