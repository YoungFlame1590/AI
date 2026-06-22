package com.printshop.quo.application;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * 折扣审批阈值配置，避免在服务代码中散落魔法值。
 */
@Component
public class DiscountRuleProperties {

    public BigDecimal storeManagerMinRate() {
        return new BigDecimal("0.90");
    }

    public BigDecimal headquartersReviewRate() {
        return new BigDecimal("0.85");
    }
}
