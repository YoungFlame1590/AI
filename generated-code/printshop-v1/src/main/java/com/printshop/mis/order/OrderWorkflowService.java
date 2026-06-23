package com.printshop.mis.order;

import com.printshop.common.exception.BusinessException;
import com.printshop.mis.delivery.DeliveryService;
import com.printshop.mis.domain.DeliveryTask;
import com.printshop.mis.domain.InvoiceRecord;
import com.printshop.mis.domain.JobTicket;
import com.printshop.mis.domain.PaymentRecord;
import com.printshop.mis.domain.PrintOrder;
import com.printshop.mis.domain.ProductionTask;
import com.printshop.mis.domain.Quotation;
import com.printshop.mis.finance.FinanceService;
import com.printshop.mis.identity.IdentityService;
import com.printshop.mis.job.JobTicketService;
import com.printshop.mis.production.ProductionTaskService;
import com.printshop.mis.quotation.QuotationService;
import com.printshop.mis.repository.JobTicketRepository;
import com.printshop.mis.repository.QuotationRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrderWorkflowService {

    private final OrderService orderService;
    private final QuotationService quotationService;
    private final JobTicketService jobTicketService;
    private final ProductionTaskService productionTaskService;
    private final DeliveryService deliveryService;
    private final FinanceService financeService;
    private final IdentityService identityService;
    private final OrderStatusPolicy statusPolicy;
    private final QuotationRepository quotations;
    private final JobTicketRepository jobTickets;

    public OrderWorkflowService(
            OrderService orderService,
            QuotationService quotationService,
            JobTicketService jobTicketService,
            ProductionTaskService productionTaskService,
            DeliveryService deliveryService,
            FinanceService financeService,
            IdentityService identityService,
            OrderStatusPolicy statusPolicy,
            QuotationRepository quotations,
            JobTicketRepository jobTickets
    ) {
        this.orderService = orderService;
        this.quotationService = quotationService;
        this.jobTicketService = jobTicketService;
        this.productionTaskService = productionTaskService;
        this.deliveryService = deliveryService;
        this.financeService = financeService;
        this.identityService = identityService;
        this.statusPolicy = statusPolicy;
        this.quotations = quotations;
        this.jobTickets = jobTickets;
    }

    public Quotation quickQuote(String username, Long orderId) {
        requireRole(username, Set.of("CLERK", "MANAGER", "ADMIN"), "生成报价");
        PrintOrder order = orderService.requireVisibleOrder(username, orderId);
        statusPolicy.requireStatus(order, Set.of(OrderStatusPolicy.REVIEWING), "生成报价", "提交审核");
        Quotation request = new Quotation();
        request.orderId = order.id;
        request.subtotal = amount(order);
        request.discountRate = BigDecimal.ONE;
        request.finalAmount = amount(order);
        request.validUntil = "7天内有效";
        return quotationService.createQuotation(username, request);
    }

    public JobTicket quickJobTicket(String username, Long orderId) {
        requireRole(username, Set.of("CLERK", "MANAGER", "ADMIN"), "生成作业单");
        PrintOrder order = orderService.requireVisibleOrder(username, orderId);
        statusPolicy.requireStatus(order, Set.of(OrderStatusPolicy.QUOTED), "生成作业单", "生成报价");
        Quotation quotation = latestQuotation(order.id);
        JobTicket request = new JobTicket();
        request.orderId = order.id;
        request.quotationId = quotation == null ? null : quotation.id;
        request.specs = order.productType + " / " + order.colorMode + " / " + order.pageCount + "页 x " + order.copies + "份";
        request.paperType = defaultPaper(order);
        request.binding = defaultBinding(order);
        return jobTicketService.createJobTicket(username, request);
    }

    public ProductionTask quickProductionTask(String username, Long orderId) {
        requireRole(username, Set.of("MANAGER", "OPS", "ADMIN"), "排产");
        PrintOrder order = orderService.requireVisibleOrder(username, orderId);
        statusPolicy.requireStatus(order, Set.of(OrderStatusPolicy.JOB_READY), "排产任务", "生成作业单");
        JobTicket ticket = latestOrCreateJobTicket(username, order);
        ProductionTask request = new ProductionTask();
        request.jobTicketId = ticket.id;
        request.station = productionStation(order);
        request.operatorName = "待分配";
        request.plannedStart = "今日";
        request.plannedEnd = "今日 18:00";
        ProductionTask task = productionTaskService.createProductionTask(username, request);
        order.status = "IN_PRODUCTION";
        order.currentStep = "生产任务已排入工位：" + task.station;
        orderService.saveOrder(order);
        return task;
    }

    public DeliveryTask quickDeliveryTask(String username, Long orderId) {
        requireRole(username, Set.of("OPS", "ADMIN"), "生成配送任务");
        PrintOrder order = orderService.requireVisibleOrder(username, orderId);
        statusPolicy.requireStatus(order, Set.of(OrderStatusPolicy.PRODUCTION_DONE), "生成配送", "完成生产质检");
        DeliveryTask request = new DeliveryTask();
        request.orderId = order.id;
        request.mode = order.deliveryMode;
        request.carrierName = "待分配";
        request.targetStore = switch (order.deliveryMode) {
            case "到店自提" -> order.storeName;
            case "跨店配送" -> "就近门店";
            case "外协配送" -> "外协服务商";
            default -> "客户地址";
        };
        DeliveryTask task = deliveryService.createDeliveryTask(username, request);
        order.status = "DELIVERING";
        order.currentStep = "配送/交付任务已生成：" + task.mode;
        orderService.saveOrder(order);
        return task;
    }

    public DeliveryTask acceptDelivery(String username, Long orderId) {
        return deliveryService.acceptDeliveryByOrder(username, orderId);
    }

    public PaymentRecord quickPayment(String username, Long orderId) {
        requireRole(username, Set.of("FINANCE", "ADMIN"), "登记收款");
        PrintOrder order = orderService.requireVisibleOrder(username, orderId);
        statusPolicy.requireStatus(order, Set.of(
                OrderStatusPolicy.QUOTED,
                OrderStatusPolicy.JOB_READY,
                OrderStatusPolicy.IN_PRODUCTION,
                OrderStatusPolicy.PRODUCTION_DONE,
                OrderStatusPolicy.DELIVERING,
                OrderStatusPolicy.DONE
        ), "登记收款", "生成报价");
        PaymentRecord request = new PaymentRecord();
        request.orderId = order.id;
        BigDecimal unpaid = amount(order).subtract(order.paidAmount == null ? BigDecimal.ZERO : order.paidAmount);
        request.amount = unpaid.signum() > 0 ? unpaid : amount(order);
        request.method = "微信";
        return financeService.createPayment(username, request);
    }

    public PaymentRecord quickRefund(String username, Long orderId) {
        requireRole(username, Set.of("CUSTOMER", "FINANCE", "ADMIN"), "生成退款");
        PrintOrder order = orderService.requireVisibleOrder(username, orderId);
        statusPolicy.requirePaid(order, "生成退款");
        return financeService.createRefundForOrder(username, orderId);
    }

    public InvoiceRecord quickInvoice(String username, Long orderId) {
        requireRole(username, Set.of("CUSTOMER", "FINANCE", "ADMIN"), "生成发票");
        PrintOrder order = orderService.requireVisibleOrder(username, orderId);
        statusPolicy.requirePaid(order, "生成发票");
        InvoiceRecord request = new InvoiceRecord();
        request.orderId = order.id;
        request.title = order.customerName;
        request.amount = amount(order);
        return financeService.createInvoice(username, request);
    }

    public Map<String, Object> quickFullFlow(String username, Long orderId) {
        requireRole(username, Set.of("ADMIN"), "一键跑完整流程");
        Map<String, Object> result = new LinkedHashMap<>();
        PrintOrder order = orderService.requireVisibleOrder(username, orderId);
        if (OrderStatusPolicy.SUBMITTED.equals(order.status)) {
            orderService.changeOrderStatus(username, orderId, Map.of("status", OrderStatusPolicy.REVIEWING, "step", "管理员已提交审核"));
        }
        result.put("quotation", quickQuote(username, orderId));
        result.put("jobTicket", quickJobTicket(username, orderId));
        result.put("productionTask", quickProductionTask(username, orderId));
        ProductionTask production = (ProductionTask) result.get("productionTask");
        result.put("productionDone", productionTaskService.completeProductionTask(username, production.id));
        result.put("deliveryTask", quickDeliveryTask(username, orderId));
        result.put("payment", quickPayment(username, orderId));
        result.put("invoice", quickInvoice(username, orderId));
        result.put("order", orderService.requireVisibleOrder(username, orderId));
        return result;
    }

    private JobTicket latestOrCreateJobTicket(String username, PrintOrder order) {
        return jobTickets.findByOrderIdOrderByCreatedAtDesc(order.id).stream()
                .findFirst()
                .orElseGet(() -> quickJobTicket(username, order.id));
    }

    private Quotation latestQuotation(Long orderId) {
        return quotations.findByOrderIdOrderByCreatedAtDesc(orderId).stream().findFirst().orElse(null);
    }

    private BigDecimal amount(PrintOrder order) {
        return order.totalAmount == null ? BigDecimal.ZERO : order.totalAmount;
    }

    private String defaultPaper(PrintOrder order) {
        return switch (order.productType) {
            case "名片快印" -> "300g铜版纸";
            case "海报写真", "写真展板" -> "写真材料";
            default -> "A4 80g";
        };
    }

    private String defaultBinding(PrintOrder order) {
        return switch (order.productType) {
            case "论文胶装" -> "胶装";
            case "培训手册" -> "骑马钉/胶装";
            case "名片快印" -> "裁切";
            default -> "按订单规格";
        };
    }

    private String productionStation(PrintOrder order) {
        return switch (order.productType) {
            case "海报写真", "写真展板" -> "写真输出-01";
            case "名片快印" -> "数码印刷-02";
            default -> "数码印刷-01";
        };
    }

    private void requireRole(String username, Set<String> roles, String action) {
        String role = identityService.requireUser(username).role;
        if (!roles.contains(role)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "当前角色不能执行：" + action);
        }
    }
}
