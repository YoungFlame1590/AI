package com.printshop.mis.finance;

import static com.printshop.mis.shared.MisSupport.code;
import static com.printshop.mis.shared.MisSupport.money;
import static com.printshop.mis.shared.MisSupport.notFound;
import static com.printshop.mis.shared.MisSupport.now;
import static com.printshop.mis.shared.MisSupport.text;

import com.printshop.mis.audit.AuditTrailService;
import com.printshop.mis.domain.InvoiceRecord;
import com.printshop.mis.domain.PaymentRecord;
import com.printshop.mis.domain.PrintOrder;
import com.printshop.mis.domain.UserAccount;
import com.printshop.mis.identity.IdentityService;
import com.printshop.mis.order.OrderService;
import com.printshop.mis.order.OrderStatusPolicy;
import com.printshop.mis.repository.InvoiceRecordRepository;
import com.printshop.mis.repository.PaymentRecordRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import com.printshop.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FinanceService {

    private static final String INVOICE_WAITING = "WAITING";
    private static final String INVOICE_ISSUED = "ISSUED";
    private static final String PAYMENT_SUCCESS = "SUCCESS";
    private static final String REFUND_REQUESTED = "REFUND_REQUESTED";
    private static final String REFUNDED = "REFUNDED";

    private final OrderService orderService;
    private final OrderStatusPolicy statusPolicy;
    private final InvoiceRecordRepository invoices;
    private final PaymentRecordRepository payments;
    private final AuditTrailService audit;
    private final IdentityService identityService;

    public FinanceService(OrderService orderService, OrderStatusPolicy statusPolicy, InvoiceRecordRepository invoices, PaymentRecordRepository payments, AuditTrailService audit, IdentityService identityService) {
        this.orderService = orderService;
        this.statusPolicy = statusPolicy;
        this.invoices = invoices;
        this.payments = payments;
        this.audit = audit;
        this.identityService = identityService;
    }

    public InvoiceRecord createInvoice(String username, InvoiceRecord request) {
        PrintOrder order = orderService.requireVisibleOrder(username, request.orderId);
        statusPolicy.requirePaid(order, "生成发票");
        requireNoRefundRecord(order.id, "开票");
        requireNoActiveInvoice(order.id);
        InvoiceRecord invoice = new InvoiceRecord();
        invoice.invoiceNo = text(request.invoiceNo, code("INV"));
        invoice.orderId = request.orderId;
        invoice.title = text(request.title, "个人");
        invoice.taxNo = text(request.taxNo, "个人无需税号");
        invoice.amount = money(request.amount);
        invoice.status = INVOICE_WAITING;
        audit.record(username, "FIN", "CREATE_INVOICE", "INVOICE", invoice.orderId, invoice.title);
        return invoices.save(invoice);
    }

    public InvoiceRecord createOrIssueInvoiceForOrder(String username, Long orderId) {
        PrintOrder order = orderService.requireVisibleOrder(username, orderId);
        statusPolicy.requirePaid(order, "生成发票");
        requireNoRefundRecord(order.id, "开票");
        UserAccount user = identityService.requireUser(username);
        List<InvoiceRecord> active = activeInvoices(order.id);
        if ("CUSTOMER".equals(user.role)) {
            if (!active.isEmpty()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "该订单已存在发票记录，不能重复申请。");
            }
            InvoiceRecord request = new InvoiceRecord();
            request.orderId = order.id;
            request.title = order.customerName;
            request.amount = order.totalAmount;
            return createInvoice(username, request);
        }
        requireFinanceWriter(username, "开票");
        return active.stream()
                .filter(invoice -> INVOICE_WAITING.equals(invoice.status))
                .findFirst()
                .map(invoice -> issueInvoice(username, invoice.id))
                .orElseGet(() -> {
                    if (active.stream().anyMatch(invoice -> INVOICE_ISSUED.equals(invoice.status))) {
                        throw new BusinessException(HttpStatus.BAD_REQUEST, "该订单已开票，不能重复开票。");
                    }
                    InvoiceRecord request = new InvoiceRecord();
                    request.orderId = order.id;
                    request.title = order.customerName;
                    request.amount = order.totalAmount;
                    InvoiceRecord invoice = createInvoice(username, request);
                    return issueInvoice(username, invoice.id);
                });
    }

    @Transactional(readOnly = true)
    public List<InvoiceRecord> invoices() {
        return invoices.findAll();
    }

    @Transactional(readOnly = true)
    public InvoiceRecord getInvoice(Long id) {
        return invoices.findById(id).orElseThrow(() -> notFound("发票", id));
    }

    public InvoiceRecord updateInvoice(String username, Long id, InvoiceRecord request) {
        InvoiceRecord invoice = getInvoice(id);
        invoice.title = text(request.title, invoice.title);
        invoice.taxNo = text(request.taxNo, invoice.taxNo);
        invoice.amount = request.amount == null ? invoice.amount : request.amount;
        invoice.status = text(request.status, invoice.status);
        audit.record(username, "FIN", "UPDATE_INVOICE", "INVOICE", id, invoice.status);
        return invoices.save(invoice);
    }

    public InvoiceRecord issueInvoice(String username, Long id) {
        requireFinanceWriter(username, "开票");
        InvoiceRecord invoice = getInvoice(id);
        if (INVOICE_ISSUED.equals(invoice.status)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该发票已开具，不能重复开票。");
        }
        invoice.taxNo = text(invoice.taxNo, "个人无需税号");
        invoice.status = INVOICE_ISSUED;
        invoice.issuedAt = now().toString();
        audit.record(username, "FIN", "ISSUE_INVOICE", "INVOICE", id, invoice.invoiceNo);
        return invoices.save(invoice);
    }

    public InvoiceRecord deleteInvoice(String username, Long id) {
        InvoiceRecord invoice = getInvoice(id);
        invoices.delete(invoice);
        audit.record(username, "FIN", "DELETE_INVOICE", "INVOICE", id, invoice.invoiceNo);
        return invoice;
    }

    public PaymentRecord createPayment(String username, PaymentRecord request) {
        requireFinanceWriter(username, "登记收款");
        PrintOrder order = orderService.requireVisibleOrder(username, request.orderId);
        statusPolicy.requireStatus(order, java.util.Set.of(
                OrderStatusPolicy.QUOTED,
                OrderStatusPolicy.JOB_READY,
                OrderStatusPolicy.IN_PRODUCTION,
                OrderStatusPolicy.PRODUCTION_DONE,
                OrderStatusPolicy.DELIVERING,
                OrderStatusPolicy.DONE
        ), "登记收款", "生成报价");
        if ("PAID".equals(order.paymentStatus)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该订单已付清，不能重复登记收款。");
        }
        if (REFUNDED.equals(order.paymentStatus) || OrderStatusPolicy.REFUNDED.equals(order.status)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该订单已退款，不能登记收款。");
        }
        BigDecimal unpaid = order.totalAmount.subtract(order.paidAmount == null ? BigDecimal.ZERO : order.paidAmount);
        BigDecimal amount = money(request.amount);
        if (amount.signum() <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "收款金额必须大于 0。");
        }
        if (amount.compareTo(unpaid) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "收款金额不能超过未收金额。");
        }
        PaymentRecord payment = new PaymentRecord();
        payment.paymentNo = text(request.paymentNo, code("PAY"));
        payment.orderId = request.orderId;
        payment.amount = amount;
        payment.method = text(request.method, "微信");
        payment.status = PAYMENT_SUCCESS;
        payment.paidAt = now().toString();
        order.paidAmount = order.paidAmount.add(payment.amount);
        order.paymentStatus = order.paidAmount.compareTo(order.totalAmount) >= 0 ? "PAID" : "PARTIAL";
        if ("PAID".equals(order.paymentStatus) && java.util.Set.of(OrderStatusPolicy.QUOTED, OrderStatusPolicy.JOB_READY).contains(order.status)) {
            order.currentStep = "收款完成，等待生产";
        } else if ("PARTIAL".equals(order.paymentStatus)) {
            order.currentStep = "部分收款完成，不改变订单主流程状态";
        }
        order.updatedAt = now();
        orderService.saveOrder(order);
        audit.record(username, "FIN", "CREATE_PAYMENT", "PAYMENT", payment.orderId, payment.amount.toPlainString());
        return payments.save(payment);
    }

    public PaymentRecord createRefundForOrder(String username, Long orderId) {
        PrintOrder order = orderService.requireVisibleOrder(username, orderId);
        statusPolicy.requirePaid(order, "生成退款");
        ensureRefundAllowed(order);
        UserAccount user = identityService.requireUser(username);
        PaymentRecord refund = new PaymentRecord();
        refund.paymentNo = code("REF");
        refund.orderId = order.id;
        refund.amount = order.paidAmount == null || order.paidAmount.signum() <= 0 ? order.totalAmount : order.paidAmount;
        refund.method = "原路退款";
        refund.status = Set.of("FINANCE", "ADMIN").contains(user.role) ? REFUNDED : REFUND_REQUESTED;
        refund.paidAt = now().toString();
        if (REFUNDED.equals(refund.status)) {
            order.paymentStatus = REFUNDED;
            order.status = OrderStatusPolicy.REFUNDED;
            order.currentStep = "退款已处理完成";
        } else {
            order.currentStep = "退款申请已提交，等待财务复核";
        }
        order.updatedAt = now();
        orderService.saveOrder(order);
        audit.record(username, "FIN", "CREATE_REFUND", "PAYMENT", order.id, refund.amount.toPlainString());
        return payments.save(refund);
    }

    public PaymentRecord requestOrProcessRefundForOrder(String username, Long orderId) {
        PrintOrder order = orderService.requireVisibleOrder(username, orderId);
        statusPolicy.requirePaid(order, "生成退款");
        UserAccount user = identityService.requireUser(username);
        if (Set.of("FINANCE", "ADMIN").contains(user.role)) {
            return payments.findByOrderIdAndStatusIn(order.id, Set.of(REFUND_REQUESTED)).stream()
                    .findFirst()
                    .map(request -> refundPayment(username, request.id))
                    .orElseGet(() -> createRefundForOrder(username, orderId));
        }
        return createRefundForOrder(username, orderId);
    }

    @Transactional(readOnly = true)
    public List<PaymentRecord> payments() {
        return payments.findAll();
    }

    @Transactional(readOnly = true)
    public PaymentRecord getPayment(Long id) {
        return payments.findById(id).orElseThrow(() -> notFound("付款记录", id));
    }

    public PaymentRecord updatePayment(String username, Long id, PaymentRecord request) {
        requireFinanceWriter(username, "更新收款");
        PaymentRecord payment = getPayment(id);
        payment.amount = request.amount == null ? payment.amount : request.amount;
        payment.method = text(request.method, payment.method);
        payment.status = text(request.status, payment.status);
        audit.record(username, "FIN", "UPDATE_PAYMENT", "PAYMENT", id, payment.status);
        return payments.save(payment);
    }

    public PaymentRecord refundPayment(String username, Long id) {
        requireFinanceWriter(username, "退款");
        PaymentRecord payment = getPayment(id);
        if (REFUNDED.equals(payment.status)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该退款已处理，不能重复退款。");
        }
        PrintOrder order = orderService.requireVisibleOrder(username, payment.orderId);
        if (OrderStatusPolicy.REFUNDED.equals(order.status) || REFUNDED.equals(order.paymentStatus)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该订单已退款，不能重复退款。");
        }
        if (!Set.of(PAYMENT_SUCCESS, REFUND_REQUESTED).contains(payment.status)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "当前付款记录状态不能退款：" + payment.status);
        }
        payment.status = REFUNDED;
        order.paymentStatus = REFUNDED;
        order.status = OrderStatusPolicy.REFUNDED;
        order.currentStep = "退款已处理完成";
        order.updatedAt = now();
        orderService.saveOrder(order);
        audit.record(username, "FIN", "REFUND_PAYMENT", "PAYMENT", id, payment.paymentNo);
        return payments.save(payment);
    }

    public PaymentRecord deletePayment(String username, Long id) {
        requireFinanceWriter(username, "删除收款");
        PaymentRecord payment = getPayment(id);
        payments.delete(payment);
        audit.record(username, "FIN", "DELETE_PAYMENT", "PAYMENT", id, payment.paymentNo);
        return payment;
    }

    private void requireFinanceWriter(String username, String action) {
        UserAccount user = identityService.requireUser(username);
        if (!java.util.Set.of("FINANCE", "ADMIN").contains(user.role)) {
            throw new com.printshop.common.exception.BusinessException(org.springframework.http.HttpStatus.FORBIDDEN, "当前角色不能执行“" + action + "”。");
        }
    }

    @Transactional(readOnly = true)
    public boolean hasActiveInvoice(Long orderId) {
        return !activeInvoices(orderId).isEmpty();
    }

    @Transactional(readOnly = true)
    public boolean hasWaitingInvoice(Long orderId) {
        return !invoices.findByOrderIdAndStatusIn(orderId, Set.of(INVOICE_WAITING)).isEmpty();
    }

    @Transactional(readOnly = true)
    public boolean hasIssuedInvoice(Long orderId) {
        return !invoices.findByOrderIdAndStatusIn(orderId, Set.of(INVOICE_ISSUED)).isEmpty();
    }

    @Transactional(readOnly = true)
    public boolean hasRefundRecord(Long orderId) {
        return !payments.findByOrderIdAndStatusIn(orderId, Set.of(REFUND_REQUESTED, REFUNDED)).isEmpty();
    }

    @Transactional(readOnly = true)
    public boolean hasRefundRequest(Long orderId) {
        return !payments.findByOrderIdAndStatusIn(orderId, Set.of(REFUND_REQUESTED)).isEmpty();
    }

    @Transactional(readOnly = true)
    public boolean hasRefundedPayment(Long orderId) {
        return !payments.findByOrderIdAndStatusIn(orderId, Set.of(REFUNDED)).isEmpty();
    }

    private void requireNoActiveInvoice(Long orderId) {
        if (hasActiveInvoice(orderId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该订单已存在发票记录，不能重复申请或开票。");
        }
    }

    private void requireNoRefundRecord(Long orderId, String action) {
        if (hasRefundRecord(orderId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该订单已存在退款申请或退款记录，不能" + action + "。");
        }
    }

    private List<InvoiceRecord> activeInvoices(Long orderId) {
        return invoices.findByOrderIdAndStatusIn(orderId, Set.of(INVOICE_WAITING, INVOICE_ISSUED));
    }

    private void ensureRefundAllowed(PrintOrder order) {
        if (OrderStatusPolicy.REFUNDED.equals(order.status) || REFUNDED.equals(order.paymentStatus)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该订单已退款，不能重复退款。");
        }
        if (hasRefundRecord(order.id)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该订单已存在退款申请或退款记录，不能重复退款。");
        }
    }
}
