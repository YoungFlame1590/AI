package com.printshop.mis.delivery;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class MockDeliveryChannelAdapter implements DeliveryChannelAdapter {

    @Override
    public DeliveryChannelQuote quote(String channelCode, BigDecimal packageWeightKg) {
        String normalized = "EXPRESS".equals(channelCode) ? "EXPRESS" : "IMMEDIATE";
        BigDecimal base = "EXPRESS".equals(normalized) ? new BigDecimal("18.00") : new BigDecimal("12.00");
        BigDecimal rate = "EXPRESS".equals(normalized) ? new BigDecimal("4.50") : new BigDecimal("3.00");
        BigDecimal fee = base.add(rate.multiply(packageWeightKg)).setScale(2, RoundingMode.HALF_UP);
        return new DeliveryChannelQuote(
                normalized,
                "EXPRESS".equals(normalized) ? "快递配送" : "即时配送",
                fee,
                "EXPRESS".equals(normalized) ? 1440 : 60
        );
    }

    @Override
    public String nextStatus(String currentStatus) {
        return switch (currentStatus == null ? "CREATED" : currentStatus) {
            case "CREATED" -> "PICKED_UP";
            case "PICKED_UP" -> "IN_TRANSIT";
            case "IN_TRANSIT" -> "DELIVERED";
            default -> "DELIVERED";
        };
    }

    @Override
    public String trackingMessage(String status) {
        return switch (status) {
            case "PICKED_UP" -> "模拟适配层：骑手/快递员已取件。";
            case "IN_TRANSIT" -> "模拟适配层：包裹正在配送途中。";
            case "DELIVERED" -> "模拟适配层：第三方平台显示已送达，等待系统签收确认。";
            default -> "模拟适配层：配送状态已更新。";
        };
    }
}
