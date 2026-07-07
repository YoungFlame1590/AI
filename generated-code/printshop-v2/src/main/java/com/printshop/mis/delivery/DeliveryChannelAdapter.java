package com.printshop.mis.delivery;

import java.math.BigDecimal;

public interface DeliveryChannelAdapter {
    DeliveryChannelQuote quote(String channelCode, BigDecimal packageWeightKg);
    String nextStatus(String currentStatus);
    String trackingMessage(String status);

    record DeliveryChannelQuote(String channelCode, String channelName, BigDecimal fee, Integer estimatedMinutes) {
    }
}
