package com.printshop.fin.application;

import com.printshop.common.exception.BusinessException;
import com.printshop.fin.dto.Invoice;
import com.printshop.fin.repository.InMemoryInvoiceRepository;
import com.printshop.infra.audit.Auditable;
import com.printshop.infra.gateway.TaxInvoiceAdapter;
import com.printshop.infra.state.StateMachine;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 财务发票应用服务。
 * 职责：开票、红冲占位和财务状态闭环。
 *
 * @see REQ-FIN-001
 */
@Service
public class InvoiceAppService {

    private static final BigDecimal AUTO_INVOICE_LIMIT = new BigDecimal("500.00");

    private final InMemoryInvoiceRepository invoiceRepository;
    private final TaxInvoiceAdapter taxInvoiceAdapter;
    private final StateMachine stateMachine;

    public InvoiceAppService(InMemoryInvoiceRepository invoiceRepository, TaxInvoiceAdapter taxInvoiceAdapter, StateMachine stateMachine) {
        this.invoiceRepository = invoiceRepository;
        this.taxInvoiceAdapter = taxInvoiceAdapter;
        this.stateMachine = stateMachine;
    }

    @Auditable(action = "ISSUE_INVOICE")
    @Transactional
    public Invoice issueInvoice(Invoice request) {
        BigDecimal amount = request.amount() == null ? BigDecimal.ZERO : request.amount();
        if (amount.compareTo(AUTO_INVOICE_LIMIT) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "自动开票金额超限，需人工复核。");
        }
        String invoiceId = hasText(request.invoiceId()) ? request.invoiceId() : "INV-" + UUID.randomUUID();
        taxInvoiceAdapter.issueInvoice(invoiceId);
        stateMachine.transit("已交付", "INVOICE_ISSUED");
        Invoice invoice = new Invoice(
                invoiceId,
                requiredOrderId(request.orderId()),
                hasText(request.invoiceStatus()) ? request.invoiceStatus() : "已开",
                amount,
                hasText(request.triggerMode()) ? request.triggerMode() : "交付后开"
        );
        return invoiceRepository.save(invoice);
    }

    private static String requiredOrderId(String orderId) {
        if (!hasText(orderId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "开票必须绑定订单号。");
        }
        return orderId;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
