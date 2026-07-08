package com.printshop.mis.order;

import static com.printshop.mis.shared.MisSupport.text;

import com.printshop.common.exception.BusinessException;
import com.printshop.mis.domain.DeliveryQuote;
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
import com.printshop.mis.repository.DeliveryTaskRepository;
import com.printshop.mis.repository.ProductionTaskRepository;
import com.printshop.mis.repository.QuotationRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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
    private final OrderWorkflowPolicy workflowPolicy;
    private final OrderChangeGuard changeGuard;
    private final OrderChangeRequestService changeRequestService;
    private final QuotationRepository quotations;
    private final JobTicketRepository jobTickets;
    private final ProductionTaskRepository productionTasks;
    private final DeliveryTaskRepository deliveryTasks;

    public OrderWorkflowService(
            OrderService orderService,
            QuotationService quotationService,
            JobTicketService jobTicketService,
            ProductionTaskService productionTaskService,
            DeliveryService deliveryService,
            FinanceService financeService,
            IdentityService identityService,
            OrderStatusPolicy statusPolicy,
            OrderWorkflowPolicy workflowPolicy,
            OrderChangeGuard changeGuard,
            OrderChangeRequestService changeRequestService,
            QuotationRepository quotations,
            JobTicketRepository jobTickets,
            ProductionTaskRepository productionTasks,
            DeliveryTaskRepository deliveryTasks
    ) {
        this.orderService = orderService;
        this.quotationService = quotationService;
        this.jobTicketService = jobTicketService;
        this.productionTaskService = productionTaskService;
        this.deliveryService = deliveryService;
        this.financeService = financeService;
        this.identityService = identityService;
        this.statusPolicy = statusPolicy;
        this.workflowPolicy = workflowPolicy;
        this.changeGuard = changeGuard;
        this.changeRequestService = changeRequestService;
        this.quotations = quotations;
        this.jobTickets = jobTickets;
        this.productionTasks = productionTasks;
        this.deliveryTasks = deliveryTasks;
    }

    public Map<String, Object> executeAction(String username, Long orderId, String action, Map<String, Object> payload) {
        String normalized = action == null ? "" : action.trim().replace('-', '_').toUpperCase();
        workflowPolicy.requireAvailable(username, orderService.requireVisibleOrder(username, orderId), normalized);
        Object result = switch (normalized) {
            case "SUBMIT_REVIEW" -> orderService.changeOrderStatus(username, orderId, Map.of(
                    "status", OrderStatusPolicy.REVIEWING,
                    "step", "订单已提交门店审核"
            ));
            case "QUOTE" -> quickQuote(username, orderId);
            case "CONFIRM_QUOTE" -> confirmLatestQuotation(username, orderId);
            case "JOB_TICKET" -> quickJobTicket(username, orderId);
            case "SCHEDULE_PRODUCTION" -> quickProductionTask(username, orderId);
            case "COMPLETE_PRODUCTION" -> productionTaskService.completeProductionTask(username, latestProductionTask(orderId).id);
            case "CREATE_DELIVERY" -> quickDeliveryTask(username, orderId);
            case "ACCEPT_DELIVERY" -> acceptDelivery(username, orderId);
            case "SIGN_DELIVERY" -> deliveryService.signDelivery(username, latestDeliveryTask(orderId).id,
                    payload == null ? Map.of() : payload);
            case "PAY" -> quickPayment(username, orderId);
            case "INVOICE" -> quickInvoice(username, orderId);
            case "REFUND" -> quickRefund(username, orderId);
            case "REQUEST_CHANGE" -> changeRequestService.createChangeRequest(username, orderId,
                    payload == null ? Map.of() : payload);
            default -> throw new BusinessException(HttpStatus.BAD_REQUEST, "未知订单工作流动作：" + action);
        };
        return workflowResponse(username, orderId, normalized, result);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> nextActions(String username, Long orderId) {
        PrintOrder order = orderService.requireVisibleOrder(username, orderId);
        List<Map<String, Object>> actions = new ArrayList<>();
        if (workflowPolicy.needsFileUpload(username, order)) {
            addAction(actions, "UPLOAD_FILE", "上传订单文件", "上传非空文件后才能提交审核");
        }
        if (workflowPolicy.available(username, order, "SUBMIT_REVIEW")) {
            addAction(actions, "SUBMIT_REVIEW", "提交审核", "订单进入门店文件审核");
        }
        if (workflowPolicy.available(username, order, "REQUEST_CHANGE")) {
            addAction(actions, "REQUEST_CHANGE", "申请订单变更", "创建订单变更请求并冻结生产/SLA");
        }
        if (workflowPolicy.available(username, order, "QUOTE")) {
            addAction(actions, "QUOTE", "生成报价", "按订单规格生成报价");
        }
        if (workflowPolicy.available(username, order, "CONFIRM_QUOTE")) {
            addAction(actions, "CONFIRM_QUOTE", "确认报价", "确认报价后才能收款和生成作业单");
        }
        if (workflowPolicy.available(username, order, "JOB_TICKET")) {
            addAction(actions, "JOB_TICKET", "生成作业单", "把报价转为生产作业单");
        }
        if (workflowPolicy.available(username, order, "SCHEDULE_PRODUCTION")) {
            addAction(actions, "SCHEDULE_PRODUCTION", "排产", "生成生产任务");
        }
        if (workflowPolicy.available(username, order, "COMPLETE_PRODUCTION")) {
            addAction(actions, "COMPLETE_PRODUCTION", "完工质检", "完成生产并通过质检");
        }
        if (workflowPolicy.available(username, order, "CREATE_DELIVERY_QUOTE")) {
            addAction(actions, "CREATE_DELIVERY_QUOTE", "第三方配送报价", "填写地址并选择即时/快递配送报价");
        }
        if (workflowPolicy.available(username, order, "CREATE_DELIVERY")) {
            addAction(actions, "CREATE_DELIVERY", "生成自营配送", "创建自提/跨店配送任务");
        }
        if (workflowPolicy.available(username, order, "ACCEPT_DELIVERY")) {
            addAction(actions, "ACCEPT_DELIVERY", "接受配送", "承接待分配配送任务");
            addAction(actions, "SIGN_DELIVERY", "签收", "登记客户签收");
        }
        if (workflowPolicy.available(username, order, "PAY")) {
            addAction(actions, "PAY", "登记收款", "按未收金额登记收款");
        }
        if (workflowPolicy.available(username, order, "INVOICE")) {
            String role = identityService.requireUser(username).role;
            if ("CUSTOMER".equals(role)) {
            addAction(actions, "INVOICE", "申请发票", "提交发票申请，等待财务处理");
            } else {
                addAction(actions, "INVOICE", "开票", "处理发票申请并生成发票记录");
            }
        }
        if (workflowPolicy.available(username, order, "REFUND")) {
            String role = identityService.requireUser(username).role;
            if ("CUSTOMER".equals(role)) {
            addAction(actions, "REFUND", "申请退款", "提交退款申请，等待财务复核");
            } else {
                addAction(actions, "REFUND", "处理退款", "生成原路退款记录");
            }
        }
        return actions;
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
        requireConfirmedQuotation(order.id, "生成作业单");
        Quotation quotation = latestQuotation(order.id);
        JobTicket request = new JobTicket();
        request.orderId = order.id;
        request.quotationId = quotation == null ? null : quotation.id;
        request.specs = order.productType + " / " + order.colorMode + " / " + text(order.sizeName, "未指定尺寸") + " / " + order.pageCount + "页 x " + order.copies + "份 / " + text(order.craftType, "无特殊工艺");
        request.paperType = text(order.paperType, defaultPaper(order));
        request.binding = text(order.craftType, defaultBinding(order));
        return jobTicketService.createJobTicket(username, request);
    }

    public ProductionTask quickProductionTask(String username, Long orderId) {
        requireRole(username, Set.of("MANAGER", "OPS", "ADMIN"), "排产");
        PrintOrder order = orderService.requireVisibleOrder(username, orderId);
        statusPolicy.requireStatus(order, Set.of(OrderStatusPolicy.JOB_READY), "排产任务", "生成作业单");
        changeGuard.requireNoPendingChange(order, "排产任务");
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
        changeGuard.requireNoPendingChange(order, "生成配送");
        if (workflowPolicy.requiresThirdPartyQuote(order)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该订单需要先进行第三方配送报价，请使用“第三方配送报价”。");
        }
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
        requireConfirmedQuotation(order.id, "登记收款");
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
        return financeService.requestOrProcessRefundForOrder(username, orderId);
    }

    public InvoiceRecord quickInvoice(String username, Long orderId) {
        requireRole(username, Set.of("CUSTOMER", "FINANCE", "ADMIN"), "生成发票");
        PrintOrder order = orderService.requireVisibleOrder(username, orderId);
        statusPolicy.requirePaid(order, "生成发票");
        return financeService.createOrIssueInvoiceForOrder(username, order.id);
    }

    public Map<String, Object> quickFullFlow(String username, Long orderId) {
        requireRole(username, Set.of("ADMIN"), "一键跑完整流程");
        Map<String, Object> result = new LinkedHashMap<>();
        PrintOrder order = orderService.requireVisibleOrder(username, orderId);
        if (OrderStatusPolicy.SUBMITTED.equals(order.status)) {
            orderService.changeOrderStatus(username, orderId, Map.of("status", OrderStatusPolicy.REVIEWING, "step", "管理员已提交审核"));
        }
        result.put("quotation", quickQuote(username, orderId));
        result.put("quoteConfirmed", confirmLatestQuotation(username, orderId));
        result.put("jobTicket", quickJobTicket(username, orderId));
        result.put("productionTask", quickProductionTask(username, orderId));
        ProductionTask production = (ProductionTask) result.get("productionTask");
        result.put("productionDone", productionTaskService.completeProductionTask(username, production.id));
        createDeliveryForFullFlow(username, orderId, result);
        result.put("payment", quickPayment(username, orderId));
        result.put("invoice", quickInvoice(username, orderId));
        result.put("order", orderService.requireVisibleOrder(username, orderId));
        return result;
    }

    private void createDeliveryForFullFlow(String username, Long orderId, Map<String, Object> result) {
        PrintOrder current = orderService.requireVisibleOrder(username, orderId);
        if (workflowPolicy.requiresThirdPartyQuote(current)) {
            DeliveryQuote quote = deliveryService.createDeliveryQuote(username, Map.of(
                    "orderId", orderId,
                    "channelCode", "外协配送".equals(current.deliveryMode) ? "EXPRESS" : "IMMEDIATE",
                    "pickupAddress", text(current.storeName, "门店"),
                    "deliveryAddress", "广州市" + text(current.storeName, "门店") + "客户地址",
                    "packageWeightKg", "1.5"
            ));
            result.put("deliveryQuote", quote);
            result.put("deliveryTask", deliveryService.confirmDeliveryQuote(username, quote.id));
            return;
        }
        result.put("deliveryTask", quickDeliveryTask(username, orderId));
    }

    private Map<String, Object> workflowResponse(String username, Long orderId, String action, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("order", orderService.requireVisibleOrder(username, orderId));
        response.put("result", result);
        response.put("nextTasks", nextActions(username, orderId));
        response.put("message", "已执行：" + action);
        return response;
    }

    private void addAction(List<Map<String, Object>> actions, String action, String label, String hint) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("action", action);
        item.put("label", label);
        item.put("hint", hint);
        actions.add(item);
    }

    private ProductionTask latestProductionTask(Long orderId) {
        return jobTickets.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .flatMap(ticket -> productionTasks.findByJobTicketId(ticket.id).stream())
                .findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "该订单没有可完工的生产任务。"));
    }

    private DeliveryTask latestDeliveryTask(Long orderId) {
        return deliveryTasks.findByOrderId(orderId).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "该订单没有可签收的配送任务。"));
    }

    private JobTicket latestOrCreateJobTicket(String username, PrintOrder order) {
        return jobTickets.findByOrderIdOrderByCreatedAtDesc(order.id).stream()
                .findFirst()
                .orElseGet(() -> quickJobTicket(username, order.id));
    }

    private Quotation latestQuotation(Long orderId) {
        return quotations.findByOrderIdOrderByCreatedAtDesc(orderId).stream().findFirst().orElse(null);
    }

    private Quotation confirmLatestQuotation(String username, Long orderId) {
        Quotation quotation = latestQuotation(orderId);
        if (quotation == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该订单没有可确认的报价。");
        }
        return quotationService.confirmQuotation(username, quotation.id);
    }

    private void requireConfirmedQuotation(Long orderId, String action) {
        if (!workflowPolicy.hasConfirmedQuotation(orderId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "不能执行“" + action + "”：请先由客户确认报价。");
        }
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
