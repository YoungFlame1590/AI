package com.printshop.infra.gateway;

import org.springframework.stereotype.Component;

/**
 * 支付网关防腐层占位适配器。
 */
@Component
public class PaymentGatewayAdapter {

    public boolean isPaymentConfirmed(String orderId) {
        return orderId != null && !orderId.isBlank();
    }
}
