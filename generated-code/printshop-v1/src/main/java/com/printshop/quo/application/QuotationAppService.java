package com.printshop.quo.application;

import com.printshop.common.exception.BusinessException;
import com.printshop.infra.audit.Auditable;
import com.printshop.quo.dto.Quotation;
import com.printshop.quo.repository.InMemoryQuotationRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 报价审批应用服务。
 * 职责：阶梯价计算与折扣决策矩阵校验。
 *
 * @see REQ-QUO-001
 * @see REQ-QUO-004
 */
@Service
public class QuotationAppService {

    private final InMemoryQuotationRepository quotationRepository;
    private final DiscountRuleEngine ruleEngine;

    public QuotationAppService(InMemoryQuotationRepository quotationRepository, DiscountRuleEngine ruleEngine) {
        this.quotationRepository = quotationRepository;
        this.ruleEngine = ruleEngine;
    }

    @Auditable(action = "CALCULATE_QUOTATION")
    public Quotation calculateQuotation(Quotation request) {
        if (request.orderId() == null || request.orderId().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "报价必须绑定订单号。");
        }
        BigDecimal finalAmount = request.finalAmount() == null ? BigDecimal.ZERO : request.finalAmount();
        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "实收金额不能为负数。");
        }
        BigDecimal discountRate = request.discountRate() == null ? BigDecimal.ONE : request.discountRate();
        Quotation quotation = new Quotation(
                hasText(request.quotationId()) ? request.quotationId() : "QUO-" + UUID.randomUUID(),
                request.orderId(),
                discountRate,
                finalAmount,
                ruleEngine.decideApprovalStatus(discountRate)
        );
        return quotationRepository.save(quotation);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
