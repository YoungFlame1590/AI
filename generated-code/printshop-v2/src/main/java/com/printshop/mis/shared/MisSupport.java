package com.printshop.mis.shared;

import com.printshop.common.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class MisSupport {

    private static final DateTimeFormatter CODE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private MisSupport() {
    }

    public static String code(String prefix) {
        return prefix + "-" + CODE_TIME.format(now()) + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    public static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public static Integer number(Integer value, Integer fallback) {
        return value == null ? fallback : value;
    }

    public static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public static Map<String, Object> metric(String label, Object value) {
        return Map.of("label", label, "value", value);
    }

    public static BusinessException notFound(String label, Long id) {
        return new BusinessException(HttpStatus.NOT_FOUND, label + "不存在：" + id);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(HttpStatus.FORBIDDEN, message);
    }
}
