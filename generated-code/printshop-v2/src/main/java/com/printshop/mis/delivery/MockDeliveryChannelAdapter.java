package com.printshop.mis.delivery;

import com.printshop.common.exception.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class MockDeliveryChannelAdapter implements DeliveryChannelAdapter {
    private static final List<String> LOCAL_IMMEDIATE_TOKENS = List.of("广州", "大学城", "中心路", "西区", "商业街", "门店");
    private static final List<String> REMOTE_CITY_TOKENS = List.of(
            "北京", "上海", "深圳", "杭州", "成都", "武汉", "南京", "重庆", "佛山", "东莞", "珠海", "长沙", "厦门", "苏州", "天津"
    );

    @Override
    public DeliveryChannelQuote quote(String channelCode, BigDecimal packageWeightKg, String pickupAddress, String deliveryAddress) {
        String normalized = "EXPRESS".equals(channelCode) ? "EXPRESS" : "IMMEDIATE";
        if ("IMMEDIATE".equals(normalized) && !isImmediateServiceArea(pickupAddress, deliveryAddress)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "超出即时配送范围，请选择快递配送。");
        }
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

    private boolean isImmediateServiceArea(String pickupAddress, String deliveryAddress) {
        String pickup = pickupAddress == null ? "" : pickupAddress;
        String delivery = deliveryAddress == null ? "" : deliveryAddress;
        // Local deterministic simulation only: real systems would call a map/distance API.
        // The demo stores are in Guangzhou, so obvious non-Guangzhou city names are treated as out of range.
        boolean looksRemote = REMOTE_CITY_TOKENS.stream().anyMatch(delivery::contains) && !delivery.contains("广州");
        if (looksRemote) {
            return false;
        }
        return LOCAL_IMMEDIATE_TOKENS.stream().anyMatch(delivery::contains)
                || LOCAL_IMMEDIATE_TOKENS.stream().anyMatch(pickup::contains);
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
