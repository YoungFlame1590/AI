package com.printshop.mis.order;

import com.printshop.common.exception.BusinessException;
import com.printshop.mis.domain.PrintOrder;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import static com.printshop.mis.shared.MisSupport.number;
import static com.printshop.mis.shared.MisSupport.text;

@Component
public class OrderPricingPolicy {

    static final Set<String> PRODUCT_TYPES = Set.of("论文胶装", "培训手册", "名片快印", "海报写真", "宣传单页", "写真展板");
    static final Set<String> COLOR_MODES = Set.of("黑白", "彩色", "黑白加彩页", "覆膜", "装订加覆膜");
    static final Set<String> DELIVERY_MODES = Set.of("到店自提", "同城配送", "跨店配送", "外协配送");
    static final Set<String> PRIORITIES = Set.of("普通", "加急", "特急");

    private static final Map<String, String> OPTION_ALIASES = Map.of(
            "批量培训手册", "培训手册",
            "黑白+彩页", "黑白加彩页",
            "彩色双面", "彩色"
    );
    private static final Map<String, BigDecimal> PRODUCT_BASE = Map.of(
            "论文胶装", new BigDecimal("18.00"),
            "培训手册", new BigDecimal("10.00"),
            "名片快印", new BigDecimal("20.00"),
            "海报写真", new BigDecimal("15.00"),
            "宣传单页", new BigDecimal("10.00"),
            "写真展板", new BigDecimal("35.00")
    );
    private static final Map<String, BigDecimal> COLOR_PAGE_RATE = Map.of(
            "黑白", new BigDecimal("0.12"),
            "彩色", new BigDecimal("0.45"),
            "黑白加彩页", new BigDecimal("0.28"),
            "覆膜", new BigDecimal("0.60"),
            "装订加覆膜", new BigDecimal("0.75")
    );
    private static final Map<String, BigDecimal> DELIVERY_FEE = Map.of(
            "到店自提", BigDecimal.ZERO,
            "同城配送", new BigDecimal("15.00"),
            "跨店配送", new BigDecimal("25.00"),
            "外协配送", new BigDecimal("40.00")
    );
    private static final Map<String, BigDecimal> PRIORITY_MULTIPLIER = Map.of(
            "普通", BigDecimal.ONE,
            "加急", new BigDecimal("1.20"),
            "特急", new BigDecimal("1.50")
    );

    public BigDecimal calculate(PrintOrder order) {
        String productType = normalizeOption(order.productType, PRODUCT_TYPES, "产品类型");
        String colorMode = normalizeOption(order.colorMode, COLOR_MODES, "颜色/工艺");
        String deliveryMode = normalizeOption(order.deliveryMode, DELIVERY_MODES, "交付方式");
        String priority = normalizeOption(order.priority, PRIORITIES, "优先级");
        int pageCount = number(order.pageCount, 1);
        int copies = number(order.copies, 1);
        validatePositive(pageCount, "页数");
        validatePositive(copies, "份数");

        BigDecimal variable = BigDecimal.valueOf((long) pageCount)
                .multiply(BigDecimal.valueOf((long) copies))
                .multiply(COLOR_PAGE_RATE.get(colorMode));
        BigDecimal base = PRODUCT_BASE.get(productType).add(variable);
        return base.multiply(PRIORITY_MULTIPLIER.get(priority))
                .add(DELIVERY_FEE.get(deliveryMode))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public String normalizeOption(String value, Set<String> allowed, String label) {
        String normalized = OPTION_ALIASES.getOrDefault(text(value, ""), value);
        if (!allowed.contains(normalized)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, label + "不在固定选项中：" + value);
        }
        return normalized;
    }

    public void validatePositive(Integer value, String label) {
        if (value == null || value <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, label + "必须大于 0。");
        }
    }
}
