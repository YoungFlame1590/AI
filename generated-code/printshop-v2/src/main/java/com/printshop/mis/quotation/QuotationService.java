package com.printshop.mis.quotation;

import static com.printshop.mis.shared.MisSupport.code;
import static com.printshop.mis.shared.MisSupport.money;
import static com.printshop.mis.shared.MisSupport.notFound;
import static com.printshop.mis.shared.MisSupport.now;
import static com.printshop.mis.shared.MisSupport.number;
import static com.printshop.mis.shared.MisSupport.text;

import com.printshop.mis.audit.AuditTrailService;
import com.printshop.mis.domain.PrintOrder;
import com.printshop.mis.domain.Quotation;
import com.printshop.mis.identity.IdentityService;
import com.printshop.mis.order.OrderService;
import com.printshop.mis.order.OrderStatusPolicy;
import com.printshop.mis.repository.QuotationRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import com.printshop.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class QuotationService {

    public static final String SENT = "SENT";
    public static final String PENDING_APPROVAL = "PENDING_APPROVAL";
    public static final String APPROVED = "APPROVED";
    public static final String CUSTOMER_CONFIRMED = "CUSTOMER_CONFIRMED";

    private final IdentityService identityService;
    private final OrderService orderService;
    private final OrderStatusPolicy statusPolicy;
    private final QuotationRepository quotations;
    private final AuditTrailService audit;

    public QuotationService(IdentityService identityService, OrderService orderService, OrderStatusPolicy statusPolicy, QuotationRepository quotations, AuditTrailService audit) {
        this.identityService = identityService;
        this.orderService = orderService;
        this.statusPolicy = statusPolicy;
        this.quotations = quotations;
        this.audit = audit;
    }

    public Quotation createQuotation(String username, Quotation request) {
        PrintOrder order = orderService.requireVisibleOrder(username, request.orderId);
        statusPolicy.requireStatus(order, java.util.Set.of(OrderStatusPolicy.REVIEWING), "生成报价", "提交审核");
        Quotation quotation = new Quotation();
        quotation.quoteNo = text(request.quoteNo, code("QUO"));
        quotation.orderId = order.id;
        quotation.versionNo = number(request.versionNo, 1);
        quotation.subtotal = money(request.subtotal);
        quotation.discountRate = request.discountRate == null ? BigDecimal.ONE : request.discountRate;
        quotation.finalAmount = request.finalAmount == null ? quotation.subtotal.multiply(quotation.discountRate) : request.finalAmount;
        quotation.status = quotation.discountRate.compareTo(new BigDecimal("0.95")) < 0 ? PENDING_APPROVAL : SENT;
        quotation.validUntil = text(request.validUntil, "7天内有效");
        quotation.createdAt = now();
        order.status = "QUOTED";
        order.totalAmount = quotation.finalAmount;
        order.currentStep = "报价已生成，等待客户确认或店长审批";
        order.updatedAt = now();
        orderService.saveOrder(order);
        audit.record(username, "QUO", "CREATE_QUOTATION", "QUOTATION", order.id, quotation.quoteNo);
        return quotations.save(quotation);
    }

    @Transactional(readOnly = true)
    public List<Quotation> quotations(String username) {
        Set<Long> visibleOrderIds = visibleOrderIds(username);
        return quotations.findAll().stream()
                .filter(quotation -> visibleOrderIds.contains(quotation.orderId))
                .toList();
    }

    @Transactional(readOnly = true)
    public Quotation getQuotation(String username, Long id) {
        Quotation quotation = quotations.findById(id).orElseThrow(() -> notFound("报价", id));
        orderService.requireVisibleOrder(username, quotation.orderId);
        return quotation;
    }

    public Quotation updateQuotation(String username, Long id, Quotation request) {
        Quotation quotation = getQuotation(username, id);
        quotation.subtotal = request.subtotal == null ? quotation.subtotal : request.subtotal;
        quotation.discountRate = request.discountRate == null ? quotation.discountRate : request.discountRate;
        quotation.finalAmount = request.finalAmount == null ? quotation.finalAmount : request.finalAmount;
        quotation.status = text(request.status, quotation.status);
        quotation.validUntil = text(request.validUntil, quotation.validUntil);
        audit.record(username, "QUO", "UPDATE_QUOTATION", "QUOTATION", id, quotation.status);
        return quotations.save(quotation);
    }

    public Quotation approveQuotation(String username, Long id) {
        Quotation quotation = getQuotation(username, id);
        quotation.status = APPROVED;
        quotation.approvedBy = identityService.requireUser(username).displayName;
        audit.record(username, "QUO", "APPROVE_QUOTATION", "QUOTATION", id, quotation.quoteNo);
        return quotations.save(quotation);
    }

    public Quotation confirmQuotation(String username, Long id) {
        Quotation quotation = getQuotation(username, id);
        PrintOrder order = orderService.requireVisibleOrder(username, quotation.orderId);
        String role = identityService.requireUser(username).role;
        if (!Set.of("CUSTOMER", "ADMIN").contains(role)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只有客户可以确认报价。");
        }
        if (!Set.of(SENT, APPROVED, CUSTOMER_CONFIRMED).contains(quotation.status)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "当前报价状态不能确认：" + quotation.status);
        }
        if (CUSTOMER_CONFIRMED.equals(quotation.status)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该报价已确认，不能重复确认。");
        }
        quotation.status = CUSTOMER_CONFIRMED;
        order.currentStep = "客户已确认报价，等待收款或生成作业单";
        order.updatedAt = now();
        orderService.saveOrder(order);
        audit.record(username, "QUO", "CONFIRM_QUOTATION", "QUOTATION", id, quotation.quoteNo);
        return quotations.save(quotation);
    }

    @Transactional(readOnly = true)
    public boolean hasConfirmedQuotation(Long orderId) {
        return quotations.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .anyMatch(quotation -> CUSTOMER_CONFIRMED.equals(quotation.status));
    }

    public Quotation deleteQuotation(String username, Long id) {
        Quotation quotation = getQuotation(username, id);
        quotations.delete(quotation);
        audit.record(username, "QUO", "DELETE_QUOTATION", "QUOTATION", id, quotation.quoteNo);
        return quotation;
    }

    private Set<Long> visibleOrderIds(String username) {
        return orderService.visibleOrders(identityService.requireUser(username)).stream()
                .map(order -> order.id)
                .collect(java.util.stream.Collectors.toSet());
    }
}
