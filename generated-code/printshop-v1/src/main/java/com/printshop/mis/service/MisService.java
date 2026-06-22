package com.printshop.mis.service;

import com.printshop.common.exception.BusinessException;
import com.printshop.infra.stats.StatsRecorder;
import com.printshop.mis.domain.AuditLogEntry;
import com.printshop.mis.domain.DeliveryTask;
import com.printshop.mis.domain.InventoryItem;
import com.printshop.mis.domain.InvoiceRecord;
import com.printshop.mis.domain.JobTicket;
import com.printshop.mis.domain.OrderFile;
import com.printshop.mis.domain.PaymentRecord;
import com.printshop.mis.domain.PrintOrder;
import com.printshop.mis.domain.ProductionTask;
import com.printshop.mis.domain.Quotation;
import com.printshop.mis.domain.Store;
import com.printshop.mis.domain.UserAccount;
import com.printshop.mis.repository.AuditLogEntryRepository;
import com.printshop.mis.repository.DeliveryTaskRepository;
import com.printshop.mis.repository.InventoryItemRepository;
import com.printshop.mis.repository.InvoiceRecordRepository;
import com.printshop.mis.repository.JobTicketRepository;
import com.printshop.mis.repository.OrderFileRepository;
import com.printshop.mis.repository.PaymentRecordRepository;
import com.printshop.mis.repository.PrintOrderRepository;
import com.printshop.mis.repository.ProductionTaskRepository;
import com.printshop.mis.repository.QuotationRepository;
import com.printshop.mis.repository.StoreRepository;
import com.printshop.mis.repository.UserAccountRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class MisService {

    private static final DateTimeFormatter CODE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final UserAccountRepository users;
    private final StoreRepository stores;
    private final PrintOrderRepository orders;
    private final OrderFileRepository files;
    private final QuotationRepository quotations;
    private final JobTicketRepository jobTickets;
    private final ProductionTaskRepository productionTasks;
    private final InventoryItemRepository inventoryItems;
    private final DeliveryTaskRepository deliveryTasks;
    private final InvoiceRecordRepository invoices;
    private final PaymentRecordRepository payments;
    private final AuditLogEntryRepository auditLogs;
    private final StatsRecorder stats;
    private final Path uploadRoot;

    public MisService(
            UserAccountRepository users,
            StoreRepository stores,
            PrintOrderRepository orders,
            OrderFileRepository files,
            QuotationRepository quotations,
            JobTicketRepository jobTickets,
            ProductionTaskRepository productionTasks,
            InventoryItemRepository inventoryItems,
            DeliveryTaskRepository deliveryTasks,
            InvoiceRecordRepository invoices,
            PaymentRecordRepository payments,
            AuditLogEntryRepository auditLogs,
            StatsRecorder stats,
            @Value("${printshop.upload-dir:uploads}") String uploadDir
    ) {
        this.users = users;
        this.stores = stores;
        this.orders = orders;
        this.files = files;
        this.quotations = quotations;
        this.jobTickets = jobTickets;
        this.productionTasks = productionTasks;
        this.inventoryItems = inventoryItems;
        this.deliveryTasks = deliveryTasks;
        this.invoices = invoices;
        this.payments = payments;
        this.auditLogs = auditLogs;
        this.stats = stats;
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public UserSession login(LoginRequest request) {
        UserAccount account = users.findByUsername(request.username())
                .filter(item -> item.password.equals(request.password()) && item.active)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "用户名或密码错误。"));
        return session(account, request.password());
    }

    public UserSession me(String username) {
        UserAccount account = requireUser(username);
        return session(account, account.password);
    }

    public Map<String, Object> dashboard(String username) {
        UserAccount user = requireUser(username);
        List<PrintOrder> visibleOrders = roleFilteredOrders(user);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", userView(user));
        result.put("metrics", List.of(
                metric("订单总数", visibleOrders.size()),
                metric("待报价/审批", countOrders(visibleOrders, "SUBMITTED", "QUOTED")),
                metric("生产/配送中", countOrders(visibleOrders, "IN_PRODUCTION", "DELIVERING")),
                metric("库存预警", inventoryItems.findAll().stream().filter(item -> item.quantity.compareTo(item.safetyStock) <= 0).count())
        ));
        result.put("orders", visibleOrders.stream().limit(8).toList());
        result.put("productionBoard", productionTasks.findAll());
        result.put("lowStock", inventoryItems.findAll().stream().filter(item -> item.quantity.compareTo(item.safetyStock) <= 0).toList());
        result.put("audits", auditLogs.findTop20ByOrderByCreatedAtDesc());
        return result;
    }

    public List<Store> stores() {
        return stores.findAll();
    }

    public List<UserAccount> users() {
        return users.findAll();
    }

    public PrintOrder createOrder(String username, PrintOrder request) {
        UserAccount user = requireUser(username);
        PrintOrder order = new PrintOrder();
        order.orderNo = text(request.orderNo, code("ORD"));
        order.customerId = user.role.equals("CUSTOMER") ? user.id : request.customerId;
        order.customerName = text(request.customerName, user.displayName);
        order.storeId = request.storeId != null ? request.storeId : user.storeId;
        order.storeName = storeName(order.storeId);
        order.productType = text(request.productType, "图文快印订单");
        order.colorMode = text(request.colorMode, "彩色");
        order.pageCount = number(request.pageCount, 1);
        order.copies = number(request.copies, 1);
        order.dueAt = text(request.dueAt, "今日 18:00");
        order.deliveryMode = text(request.deliveryMode, "到店自提");
        order.priority = text(request.priority, "普通");
        order.status = "SUBMITTED";
        order.paymentStatus = "UNPAID";
        order.currentStep = "客户已提交订单，等待门店审核文件";
        order.totalAmount = money(request.totalAmount);
        order.paidAmount = money(request.paidAmount);
        order.createdAt = now();
        order.updatedAt = now();
        PrintOrder saved = orders.save(order);
        record(username, "ORD", "CREATE_ORDER", "ORDER", saved.id, saved.orderNo);
        return saved;
    }

    public List<PrintOrder> listOrders(String username) {
        return roleFilteredOrders(requireUser(username));
    }

    public PrintOrder getOrder(Long id) {
        return orders.findById(id).orElseThrow(() -> notFound("订单", id));
    }

    public PrintOrder updateOrder(String username, Long id, PrintOrder request) {
        PrintOrder order = getOrder(id);
        order.productType = text(request.productType, order.productType);
        order.colorMode = text(request.colorMode, order.colorMode);
        order.pageCount = number(request.pageCount, order.pageCount);
        order.copies = number(request.copies, order.copies);
        order.dueAt = text(request.dueAt, order.dueAt);
        order.deliveryMode = text(request.deliveryMode, order.deliveryMode);
        order.priority = text(request.priority, order.priority);
        order.status = text(request.status, order.status);
        order.paymentStatus = text(request.paymentStatus, order.paymentStatus);
        order.currentStep = text(request.currentStep, "订单信息已更新");
        order.totalAmount = request.totalAmount == null ? order.totalAmount : request.totalAmount;
        order.updatedAt = now();
        record(username, "ORD", "UPDATE_ORDER", "ORDER", order.id, order.orderNo);
        return orders.save(order);
    }

    public PrintOrder changeOrderStatus(String username, Long id, Map<String, Object> payload) {
        PrintOrder order = getOrder(id);
        order.status = text(asString(payload.get("status")), order.status);
        order.currentStep = text(asString(payload.get("step")), "订单状态已流转");
        order.updatedAt = now();
        record(username, "ORD", "CHANGE_ORDER_STATUS", "ORDER", order.id, order.status);
        return orders.save(order);
    }

    public PrintOrder deleteOrder(String username, Long id) {
        PrintOrder order = getOrder(id);
        orders.delete(order);
        record(username, "ORD", "DELETE_ORDER", "ORDER", id, order.orderNo);
        return order;
    }

    public OrderFile uploadFile(String username, Long orderId, MultipartFile upload) {
        PrintOrder order = getOrder(orderId);
        try {
            Files.createDirectories(uploadRoot);
            String safeName = UUID.randomUUID() + "-" + upload.getOriginalFilename();
            Path target = uploadRoot.resolve(safeName).normalize();
            upload.transferTo(target);
            OrderFile file = new OrderFile();
            file.orderId = orderId;
            file.fileName = upload.getOriginalFilename();
            file.filePath = target.toString();
            file.sizeBytes = upload.getSize();
            file.fileStatus = "UPLOADED";
            file.uploadedAt = now();
            order.currentStep = "文件已上传，等待文件检查";
            order.updatedAt = now();
            orders.save(order);
            record(username, "ORD", "UPLOAD_FILE", "ORDER_FILE", orderId, file.fileName);
            return files.save(file);
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "文件保存失败：" + ex.getMessage());
        }
    }

    public List<OrderFile> orderFiles(Long orderId) {
        return files.findByOrderIdOrderByUploadedAtDesc(orderId);
    }

    public Quotation createQuotation(String username, Quotation request) {
        PrintOrder order = getOrder(request.orderId);
        Quotation quotation = new Quotation();
        quotation.quoteNo = text(request.quoteNo, code("QUO"));
        quotation.orderId = order.id;
        quotation.versionNo = number(request.versionNo, 1);
        quotation.subtotal = money(request.subtotal);
        quotation.discountRate = request.discountRate == null ? BigDecimal.ONE : request.discountRate;
        quotation.finalAmount = request.finalAmount == null ? quotation.subtotal.multiply(quotation.discountRate) : request.finalAmount;
        quotation.status = quotation.discountRate.compareTo(new BigDecimal("0.95")) < 0 ? "PENDING_APPROVAL" : "SENT";
        quotation.validUntil = text(request.validUntil, "7天内有效");
        quotation.createdAt = now();
        order.status = "QUOTED";
        order.totalAmount = quotation.finalAmount;
        order.currentStep = "报价已生成，等待客户确认或店长审批";
        order.updatedAt = now();
        orders.save(order);
        record(username, "QUO", "CREATE_QUOTATION", "QUOTATION", order.id, quotation.quoteNo);
        return quotations.save(quotation);
    }

    public List<Quotation> quotations() {
        return quotations.findAll();
    }

    public Quotation getQuotation(Long id) {
        return quotations.findById(id).orElseThrow(() -> notFound("报价", id));
    }

    public Quotation updateQuotation(String username, Long id, Quotation request) {
        Quotation quotation = getQuotation(id);
        quotation.subtotal = request.subtotal == null ? quotation.subtotal : request.subtotal;
        quotation.discountRate = request.discountRate == null ? quotation.discountRate : request.discountRate;
        quotation.finalAmount = request.finalAmount == null ? quotation.finalAmount : request.finalAmount;
        quotation.status = text(request.status, quotation.status);
        quotation.validUntil = text(request.validUntil, quotation.validUntil);
        record(username, "QUO", "UPDATE_QUOTATION", "QUOTATION", id, quotation.status);
        return quotations.save(quotation);
    }

    public Quotation approveQuotation(String username, Long id) {
        Quotation quotation = getQuotation(id);
        quotation.status = "APPROVED";
        quotation.approvedBy = requireUser(username).displayName;
        record(username, "QUO", "APPROVE_QUOTATION", "QUOTATION", id, quotation.quoteNo);
        return quotations.save(quotation);
    }

    public Quotation deleteQuotation(String username, Long id) {
        Quotation quotation = getQuotation(id);
        quotations.delete(quotation);
        record(username, "QUO", "DELETE_QUOTATION", "QUOTATION", id, quotation.quoteNo);
        return quotation;
    }

    public JobTicket createJobTicket(String username, JobTicket request) {
        PrintOrder order = getOrder(request.orderId);
        JobTicket ticket = new JobTicket();
        ticket.ticketNo = text(request.ticketNo, code("JOB"));
        ticket.orderId = order.id;
        ticket.quotationId = request.quotationId;
        ticket.specs = text(request.specs, order.productType + " / " + order.colorMode);
        ticket.paperType = text(request.paperType, "A4 80g");
        ticket.binding = text(request.binding, "普通装订");
        ticket.status = "READY";
        ticket.createdAt = now();
        order.status = "JOB_READY";
        order.currentStep = "作业单已生成，等待排产";
        order.updatedAt = now();
        orders.save(order);
        record(username, "PRO", "CREATE_JOB_TICKET", "JOB_TICKET", order.id, ticket.ticketNo);
        return jobTickets.save(ticket);
    }

    public List<JobTicket> jobTickets() {
        return jobTickets.findAll();
    }

    public JobTicket getJobTicket(Long id) {
        return jobTickets.findById(id).orElseThrow(() -> notFound("作业单", id));
    }

    public JobTicket updateJobTicket(String username, Long id, JobTicket request) {
        JobTicket ticket = getJobTicket(id);
        ticket.specs = text(request.specs, ticket.specs);
        ticket.paperType = text(request.paperType, ticket.paperType);
        ticket.binding = text(request.binding, ticket.binding);
        ticket.status = text(request.status, ticket.status);
        record(username, "PRO", "UPDATE_JOB_TICKET", "JOB_TICKET", id, ticket.status);
        return jobTickets.save(ticket);
    }

    public JobTicket deleteJobTicket(String username, Long id) {
        JobTicket ticket = getJobTicket(id);
        jobTickets.delete(ticket);
        record(username, "PRO", "DELETE_JOB_TICKET", "JOB_TICKET", id, ticket.ticketNo);
        return ticket;
    }

    public ProductionTask createProductionTask(String username, ProductionTask request) {
        ProductionTask task = new ProductionTask();
        task.taskNo = text(request.taskNo, code("PRO"));
        task.jobTicketId = request.jobTicketId;
        task.station = text(request.station, "数码印刷-01");
        task.operatorName = text(request.operatorName, "待分配");
        task.plannedStart = text(request.plannedStart, "今日");
        task.plannedEnd = text(request.plannedEnd, "今日 18:00");
        task.status = "SCHEDULED";
        task.progressPercent = number(request.progressPercent, 0);
        task.qualityStatus = text(request.qualityStatus, "PENDING");
        record(username, "PRO", "CREATE_PRODUCTION_TASK", "PRODUCTION_TASK", task.jobTicketId, task.station);
        return productionTasks.save(task);
    }

    public List<ProductionTask> productionTasks() {
        return productionTasks.findAll();
    }

    public ProductionTask getProductionTask(Long id) {
        return productionTasks.findById(id).orElseThrow(() -> notFound("生产任务", id));
    }

    public ProductionTask updateProductionTask(String username, Long id, ProductionTask request) {
        ProductionTask task = getProductionTask(id);
        task.station = text(request.station, task.station);
        task.operatorName = text(request.operatorName, task.operatorName);
        task.plannedStart = text(request.plannedStart, task.plannedStart);
        task.plannedEnd = text(request.plannedEnd, task.plannedEnd);
        task.status = text(request.status, task.status);
        task.progressPercent = number(request.progressPercent, task.progressPercent);
        task.qualityStatus = text(request.qualityStatus, task.qualityStatus);
        record(username, "PRO", "UPDATE_PRODUCTION_TASK", "PRODUCTION_TASK", id, task.status);
        return productionTasks.save(task);
    }

    public ProductionTask completeProductionTask(String username, Long id) {
        ProductionTask task = getProductionTask(id);
        task.status = "DONE";
        task.progressPercent = 100;
        task.qualityStatus = "PASS";
        record(username, "PRO", "COMPLETE_PRODUCTION_TASK", "PRODUCTION_TASK", id, task.taskNo);
        return productionTasks.save(task);
    }

    public ProductionTask deleteProductionTask(String username, Long id) {
        ProductionTask task = getProductionTask(id);
        productionTasks.delete(task);
        record(username, "PRO", "DELETE_PRODUCTION_TASK", "PRODUCTION_TASK", id, task.taskNo);
        return task;
    }

    public InventoryItem saveInventory(String username, InventoryItem request) {
        InventoryItem item = request.id == null ? new InventoryItem() : getInventoryItem(request.id);
        item.sku = text(request.sku, item.sku == null ? code("SKU") : item.sku);
        item.itemName = text(request.itemName, "未命名物料");
        item.category = text(request.category, item.category);
        item.unit = text(request.unit, "件");
        item.quantity = request.quantity == null ? item.quantity : request.quantity;
        item.safetyStock = request.safetyStock == null ? item.safetyStock : request.safetyStock;
        item.location = text(request.location, item.location);
        record(username, "INV", "SAVE_INVENTORY", "INVENTORY", item.id, item.sku);
        return inventoryItems.save(item);
    }

    public List<InventoryItem> inventoryItems() {
        return inventoryItems.findAll();
    }

    public InventoryItem getInventoryItem(Long id) {
        return inventoryItems.findById(id).orElseThrow(() -> notFound("库存物料", id));
    }

    public InventoryItem adjustInventory(String username, Long id, Map<String, Object> payload) {
        InventoryItem item = getInventoryItem(id);
        item.quantity = item.quantity.add(new BigDecimal(String.valueOf(payload.getOrDefault("delta", "0"))));
        record(username, "INV", "ADJUST_INVENTORY", "INVENTORY", id, item.quantity.toPlainString());
        return inventoryItems.save(item);
    }

    public InventoryItem deleteInventory(String username, Long id) {
        InventoryItem item = getInventoryItem(id);
        inventoryItems.delete(item);
        record(username, "INV", "DELETE_INVENTORY", "INVENTORY", id, item.sku);
        return item;
    }

    public DeliveryTask createDeliveryTask(String username, DeliveryTask request) {
        DeliveryTask task = new DeliveryTask();
        task.taskNo = text(request.taskNo, code("DLV"));
        task.orderId = request.orderId;
        task.mode = text(request.mode, "到店自提");
        task.carrierName = text(request.carrierName, "待分配");
        task.targetStore = text(request.targetStore, "客户地址");
        task.status = "ASSIGNED";
        task.signedBy = request.signedBy;
        task.updatedAt = now();
        record(username, "DLV", "CREATE_DELIVERY_TASK", "DELIVERY_TASK", task.orderId, task.mode);
        return deliveryTasks.save(task);
    }

    public List<DeliveryTask> deliveryTasks() {
        return deliveryTasks.findAll();
    }

    public DeliveryTask getDeliveryTask(Long id) {
        return deliveryTasks.findById(id).orElseThrow(() -> notFound("配送任务", id));
    }

    public DeliveryTask updateDeliveryTask(String username, Long id, DeliveryTask request) {
        DeliveryTask task = getDeliveryTask(id);
        task.mode = text(request.mode, task.mode);
        task.carrierName = text(request.carrierName, task.carrierName);
        task.targetStore = text(request.targetStore, task.targetStore);
        task.status = text(request.status, task.status);
        task.signedBy = text(request.signedBy, task.signedBy);
        task.updatedAt = now();
        record(username, "DLV", "UPDATE_DELIVERY_TASK", "DELIVERY_TASK", id, task.status);
        return deliveryTasks.save(task);
    }

    public DeliveryTask signDelivery(String username, Long id, Map<String, Object> payload) {
        DeliveryTask task = getDeliveryTask(id);
        task.status = "SIGNED";
        task.signedBy = text(asString(payload.get("signedBy")), requireUser(username).displayName);
        task.updatedAt = now();
        record(username, "DLV", "SIGN_DELIVERY", "DELIVERY_TASK", id, task.signedBy);
        return deliveryTasks.save(task);
    }

    public DeliveryTask deleteDeliveryTask(String username, Long id) {
        DeliveryTask task = getDeliveryTask(id);
        deliveryTasks.delete(task);
        record(username, "DLV", "DELETE_DELIVERY_TASK", "DELIVERY_TASK", id, task.taskNo);
        return task;
    }

    public InvoiceRecord createInvoice(String username, InvoiceRecord request) {
        InvoiceRecord invoice = new InvoiceRecord();
        invoice.invoiceNo = text(request.invoiceNo, code("INV"));
        invoice.orderId = request.orderId;
        invoice.title = text(request.title, "个人");
        invoice.taxNo = request.taxNo;
        invoice.amount = money(request.amount);
        invoice.status = "WAITING";
        record(username, "FIN", "CREATE_INVOICE", "INVOICE", invoice.orderId, invoice.title);
        return invoices.save(invoice);
    }

    public List<InvoiceRecord> invoices() {
        return invoices.findAll();
    }

    public InvoiceRecord getInvoice(Long id) {
        return invoices.findById(id).orElseThrow(() -> notFound("发票", id));
    }

    public InvoiceRecord updateInvoice(String username, Long id, InvoiceRecord request) {
        InvoiceRecord invoice = getInvoice(id);
        invoice.title = text(request.title, invoice.title);
        invoice.taxNo = text(request.taxNo, invoice.taxNo);
        invoice.amount = request.amount == null ? invoice.amount : request.amount;
        invoice.status = text(request.status, invoice.status);
        record(username, "FIN", "UPDATE_INVOICE", "INVOICE", id, invoice.status);
        return invoices.save(invoice);
    }

    public InvoiceRecord issueInvoice(String username, Long id) {
        InvoiceRecord invoice = getInvoice(id);
        invoice.status = "ISSUED";
        invoice.issuedAt = now().toString();
        record(username, "FIN", "ISSUE_INVOICE", "INVOICE", id, invoice.invoiceNo);
        return invoices.save(invoice);
    }

    public InvoiceRecord deleteInvoice(String username, Long id) {
        InvoiceRecord invoice = getInvoice(id);
        invoices.delete(invoice);
        record(username, "FIN", "DELETE_INVOICE", "INVOICE", id, invoice.invoiceNo);
        return invoice;
    }

    public PaymentRecord createPayment(String username, PaymentRecord request) {
        PaymentRecord payment = new PaymentRecord();
        payment.paymentNo = text(request.paymentNo, code("PAY"));
        payment.orderId = request.orderId;
        payment.amount = money(request.amount);
        payment.method = text(request.method, "微信");
        payment.status = "SUCCESS";
        payment.paidAt = now().toString();
        PrintOrder order = getOrder(request.orderId);
        order.paidAmount = order.paidAmount.add(payment.amount);
        order.paymentStatus = order.paidAmount.compareTo(order.totalAmount) >= 0 ? "PAID" : "PARTIAL";
        order.updatedAt = now();
        orders.save(order);
        record(username, "FIN", "CREATE_PAYMENT", "PAYMENT", payment.orderId, payment.amount.toPlainString());
        return payments.save(payment);
    }

    public List<PaymentRecord> payments() {
        return payments.findAll();
    }

    public PaymentRecord getPayment(Long id) {
        return payments.findById(id).orElseThrow(() -> notFound("付款记录", id));
    }

    public PaymentRecord updatePayment(String username, Long id, PaymentRecord request) {
        PaymentRecord payment = getPayment(id);
        payment.amount = request.amount == null ? payment.amount : request.amount;
        payment.method = text(request.method, payment.method);
        payment.status = text(request.status, payment.status);
        record(username, "FIN", "UPDATE_PAYMENT", "PAYMENT", id, payment.status);
        return payments.save(payment);
    }

    public PaymentRecord refundPayment(String username, Long id) {
        PaymentRecord payment = getPayment(id);
        payment.status = "REFUNDED";
        record(username, "FIN", "REFUND_PAYMENT", "PAYMENT", id, payment.paymentNo);
        return payments.save(payment);
    }

    public PaymentRecord deletePayment(String username, Long id) {
        PaymentRecord payment = getPayment(id);
        payments.delete(payment);
        record(username, "FIN", "DELETE_PAYMENT", "PAYMENT", id, payment.paymentNo);
        return payment;
    }

    public List<AuditLogEntry> auditLogs() {
        return auditLogs.findTop20ByOrderByCreatedAtDesc();
    }

    public AuditLogEntry getAuditLog(Long id) {
        return auditLogs.findById(id).orElseThrow(() -> notFound("审计日志", id));
    }

    public Map<String, Object> reports() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderFunnel", Map.of(
                "submitted", countAllOrders("SUBMITTED"),
                "quoted", countAllOrders("QUOTED"),
                "jobReady", countAllOrders("JOB_READY"),
                "production", countAllOrders("IN_PRODUCTION")
        ));
        result.put("finance", Map.of(
                "invoiceCount", invoices.count(),
                "paymentCount", payments.count()
        ));
        result.put("productionLoad", productionTasks.findAll());
        result.put("lowStock", inventoryItems.findAll().stream().filter(item -> item.quantity.compareTo(item.safetyStock) <= 0).toList());
        return result;
    }

    public void record(String username, String moduleCode, String action, String targetType, Object targetId, String detail) {
        UserAccount user = username == null ? null : users.findByUsername(username).orElse(null);
        AuditLogEntry entry = new AuditLogEntry();
        entry.operator = user == null ? "system" : user.displayName;
        entry.role = user == null ? "ADMIN" : user.role;
        entry.action = action;
        entry.targetType = targetType;
        entry.targetId = targetId == null ? "-" : String.valueOf(targetId);
        entry.detail = detail;
        entry.createdAt = now();
        auditLogs.save(entry);
        stats.record(moduleCode);
    }

    private UserAccount requireUser(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "登录状态无效。"));
    }

    private UserSession session(UserAccount account, String password) {
        String raw = account.username + ":" + password;
        return new UserSession(
                userView(account),
                Base64.getEncoder().encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8))
        );
    }

    private Map<String, Object> userView(UserAccount user) {
        Store store = user.storeId == null ? null : stores.findById(user.storeId).orElse(null);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", user.id);
        view.put("username", user.username);
        view.put("role", user.role);
        view.put("displayName", user.displayName);
        view.put("storeId", user.storeId);
        view.put("storeName", store == null ? "总部" : store.name);
        return view;
    }

    private List<PrintOrder> roleFilteredOrders(UserAccount user) {
        if ("CUSTOMER".equals(user.role)) {
            return orders.findByCustomerIdOrderByUpdatedAtDesc(user.id);
        }
        return orders.findTop8ByOrderByUpdatedAtDesc();
    }

    private long countOrders(List<PrintOrder> source, String... statuses) {
        return source.stream().filter(order -> {
            for (String status : statuses) {
                if (status.equals(order.status)) {
                    return true;
                }
            }
            return false;
        }).count();
    }

    private long countAllOrders(String status) {
        return orders.findAll().stream().filter(order -> status.equals(order.status)).count();
    }

    private Map<String, Object> metric(String label, Object value) {
        return Map.of("label", label, "value", value);
    }

    private String storeName(Long storeId) {
        if (storeId == null) {
            return "总部";
        }
        return stores.findById(storeId).map(store -> store.name).orElse("未知门店");
    }

    private BusinessException notFound(String label, Long id) {
        return new BusinessException(HttpStatus.NOT_FOUND, label + "不存在：" + id);
    }

    private String code(String prefix) {
        return prefix + "-" + CODE_TIME.format(now()) + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    private LocalDateTime now() {
        return LocalDateTime.now();
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Integer number(Integer value, Integer fallback) {
        return value == null ? fallback : value;
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public record LoginRequest(String username, String password) {
    }

    public record UserSession(Map<String, Object> user, String token) {
    }
}
