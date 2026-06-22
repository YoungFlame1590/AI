package com.printshop.quo.application;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * 折扣决策矩阵规则引擎。
 *
 * @see REQ-QUO-004
 */
@Component
public class DiscountRuleEngine {

    private final DiscountRuleProperties properties;

    public DiscountRuleEngine(DiscountRuleProperties properties) {
        this.properties = properties;
    }

    public String decideApprovalStatus(BigDecimal discountRate) {
        BigDecimal rate = discountRate == null ? BigDecimal.ONE : discountRate;
        if (rate.compareTo(properties.storeManagerMinRate()) >= 0) {
            return "店长直批";
        }
        if (rate.compareTo(properties.headquartersReviewRate()) >= 0) {
            return "总部审批";
        }
        return "总部终审";
    }
}
