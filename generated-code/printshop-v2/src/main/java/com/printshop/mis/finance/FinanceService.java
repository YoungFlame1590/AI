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
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FinanceService {

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
        InvoiceRecord invoice = new InvoiceRecord();
        invoice.invoiceNo = text(request.invoiceNo, code("INV"));
        invoice.orderId = request.orderId;
        invoice.title = text(request.title, "个人");
        invoice.taxNo = text(request.taxNo, "个人无需税号");
        invoice.amount = money(request.amount);
        invoice.status = "WAITING";
        audit.record(username, "FIN", "CREATE_INVOICE", "INVOICE", invoice.orderId, invoice.title);
        return invoices.save(invoice);
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
        InvoiceRecord invoice = getInvoice(id);
        invoice.taxNo = text(invoice.taxNo, "个人无需税号");
        invoice.status = "ISSUED";
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
        PaymentRecord payment = new PaymentRecord();
        payment.paymentNo = text(request.paymentNo, code("PAY"));
        payment.orderId = request.orderId;
        payment.amount = money(request.amount);
        payment.method = text(request.method, "微信");
        payment.status = "SUCCESS";
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
        PaymentRecord refund = new PaymentRecord();
        refund.paymentNo = code("REF");
        refund.orderId = order.id;
        refund.amount = order.paidAmount == null || order.paidAmount.signum() <= 0 ? order.totalAmount : order.paidAmount;
        refund.method = "原路退款";
        refund.status = "REFUNDED";
        refund.paidAt = now().toString();
        order.paymentStatus = "REFUNDED";
        order.status = "REFUNDED";
        order.currentStep = "退款记录已生成，等待财务复核";
        order.updatedAt = now();
        orderService.saveOrder(order);
        audit.record(username, "FIN", "CREATE_REFUND", "PAYMENT", order.id, refund.amount.toPlainString());
        return payments.save(refund);
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
        payment.status = "REFUNDED";
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
}
