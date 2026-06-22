package com.printshop.workbench.application;

import com.printshop.aud.application.AuditLogAppService;
import com.printshop.aud.dto.AuditLog;
import com.printshop.common.exception.BusinessException;
import com.printshop.infra.audit.AuditRecorder;
import com.printshop.infra.stats.StatsRecorder;
import com.printshop.workbench.dto.RoleAction;
import com.printshop.workbench.dto.RoleProfile;
import com.printshop.workbench.dto.WorkbenchActionRequest;
import com.printshop.workbench.dto.WorkbenchMetric;
import com.printshop.workbench.dto.WorkbenchOrder;
import com.printshop.workbench.dto.WorkbenchSnapshot;
import com.printshop.workbench.dto.WorkbenchTask;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * v1 角色工作台 Facade，使用内存种子数据支撑课程演示。
 */
@Service
public class WorkbenchService {

    private final StatsRecorder statsRecorder;
    private final AuditRecorder auditRecorder;
    private final AuditLogAppService auditLogAppService;
    private final Map<String, RoleProfile> roles = new LinkedHashMap<>();
    private final Map<String, DemoOrder> orders = new LinkedHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger(3000);

    public WorkbenchService(StatsRecorder statsRecorder, AuditRecorder auditRecorder, AuditLogAppService auditLogAppService) {
        this.statsRecorder = statsRecorder;
        this.auditRecorder = auditRecorder;
        this.auditLogAppService = auditLogAppService;
        seedRoles();
        resetDemoData();
    }

    public synchronized List<RoleProfile> roles() {
        return List.copyOf(roles.values());
    }

    public synchronized WorkbenchSnapshot snapshot(String roleId) {
        return snapshot(roleId, "工作台已刷新");
    }

    public synchronized WorkbenchSnapshot runAction(WorkbenchActionRequest request) {
        RoleProfile role = requireRole(request.roleId());
        RoleAction action = requireAction(role.roleId(), request.action());
        DemoOrder order = action.orderRequired() ? requireOrder(request.orderId()) : null;

        switch (action.actionId()) {
            case "customer_create_order" -> order = createCustomerOrder();
            case "customer_request_invoice" -> update(order, "待开票", order.quoteStatus, order.productionStatus,
                    order.deliveryStatus, "待开票", "待核销", "客户已申请发票");
            case "customer_confirm_pickup" -> update(order, "已取件", order.quoteStatus, order.productionStatus,
                    "已签收", order.invoiceStatus, order.financeStatus, "客户已确认取件");
            case "clerk_accept_order" -> update(order, "待报价", "待报价", order.productionStatus,
                    order.deliveryStatus, order.invoiceStatus, order.financeStatus, "店员已接单并核对文件");
            case "clerk_quote_order" -> {
                order.amount = amountFromPayload(request.payload(), order.amount);
                update(order, "待支付", "已报价", order.productionStatus, order.deliveryStatus,
                        order.invoiceStatus, order.financeStatus, "店员已完成报价");
            }
            case "clerk_mark_exception" -> {
                order.tags.add("异常备注");
                update(order, "异常待处理", order.quoteStatus, order.productionStatus, order.deliveryStatus,
                        order.invoiceStatus, order.financeStatus, "店员已标记文件/客户异常");
            }
            case "manager_approve_discount" -> update(order, "待排产", "折扣已批", order.productionStatus,
                    order.deliveryStatus, order.invoiceStatus, order.financeStatus, "店长已批准折扣");
            case "manager_dispatch_override" -> update(order, "生产中", order.quoteStatus, "人工排产",
                    order.deliveryStatus, order.invoiceStatus, order.financeStatus, "店长已人工调整排产");
            case "ops_route_order" -> update(order, "待配送", order.quoteStatus, order.productionStatus,
                    "跨店路由已生成", order.invoiceStatus, "待核销-耗材", "总部已生成跨店路由");
            case "ops_update_rule" -> recordGlobal(role, action, "路由权重已更新为 SLA 优先");
            case "finance_issue_invoice" -> update(order, order.status, order.quoteStatus, order.productionStatus,
                    order.deliveryStatus, "已开票", "已核销", "财务已开具电子发票");
            case "finance_reconcile" -> update(order, order.status, order.quoteStatus, order.productionStatus,
                    order.deliveryStatus, order.invoiceStatus, "已日结", "财务已完成日结核对");
            case "courier_accept_delivery" -> update(order, "配送中", order.quoteStatus, order.productionStatus,
                    "配送已接单", order.invoiceStatus, order.financeStatus, "配送外协已接单");
            case "courier_sign_order" -> update(order, "已交付", order.quoteStatus, order.productionStatus,
                    "已签收", order.invoiceStatus, order.financeStatus, "配送外协已完成签收");
            case "courier_report_exception" -> {
                order.tags.add("配送异常");
                update(order, "异常待处理", order.quoteStatus, order.productionStatus, "配送异常",
                        order.invoiceStatus, order.financeStatus, "配送外协已报备异常");
            }
            case "admin_export_audit" -> recordGlobal(role, action, "系统管理员已导出脱敏审计摘要");
            case "admin_update_config" -> recordGlobal(role, action, "系统管理员已保存权限/配置快照");
            default -> throw new BusinessException(HttpStatus.BAD_REQUEST, "未知工作台动作。");
        }

        String moduleCode = action.moduleCode();
        statsRecorder.record(moduleCode);
        if (order != null) {
            auditRecorder.record(role.userId(), action.actionId(), order.orderId + " " + order.currentStep);
        }
        return snapshot(role.roleId(), action.label() + "完成");
    }

    public synchronized WorkbenchSnapshot reset(String roleId) {
        resetDemoData();
        auditRecorder.record("system", "RESET_DEMO_DATA", "v1 角色工作台演示数据已重置");
        return snapshot(roleId, "演示数据已重置");
    }

    private WorkbenchSnapshot snapshot(String roleId, String message) {
        RoleProfile role = requireRole(roleId);
        List<DemoOrder> visibleOrders = visibleOrders(role.roleId());
        return new WorkbenchSnapshot(
                role,
                metrics(role.roleId(), visibleOrders),
                tasks(role.roleId(), visibleOrders),
                visibleOrders.stream().map(this::toOrderView).toList(),
                actions(role.roleId()),
                recentAudits(),
                statsRecorder.snapshot(),
                message
        );
    }

    private void seedRoles() {
        roles.put("customer", new RoleProfile("customer", "客户", "CUST-001", "张同学", "ORD", "下单、进度、取件与发票"));
        roles.put("clerk", new RoleProfile("clerk", "门店店员", "EMP-101", "前台小周", "ORD", "接单、报价、异常备注"));
        roles.put("manager", new RoleProfile("manager", "门店店长", "MGR-201", "店长林", "QUO", "折扣审批与排产干预"));
        roles.put("ops", new RoleProfile("ops", "总部运营管理员", "OPS-301", "运营许", "DLV", "跨店路由与规则配置"));
        roles.put("finance", new RoleProfile("finance", "财务人员", "FIN-401", "财务陈", "FIN", "开票、日结与差异核对"));
        roles.put("courier", new RoleProfile("courier", "配送/外协人员", "DLV-501", "配送赵", "DLV", "配送接单、签收与异常报备"));
        roles.put("admin", new RoleProfile("admin", "系统管理员", "ADM-901", "系统管理员", "AUD", "审计、权限与配置留痕"));
    }

    private void resetDemoData() {
        orders.clear();
        add(new DemoOrder("ORD-1001", "张同学", "大学城店", "待接单", "未报价", "未排产", "未配送", "未申请", "待核销",
                "普通", new BigDecimal("128.00"), "客户已上传文件，等待店员接单", List.of("A4彩印", "自提")));
        add(new DemoOrder("ORD-1002", "行政王老师", "大学城店", "待审批", "折扣待店长审批", "未排产", "未配送", "未申请", "待核销",
                "高", new BigDecimal("980.00"), "折扣越界，等待店长审批", List.of("批量胶装", "折扣")));
        add(new DemoOrder("ORD-1003", "商户李先生", "市中心店", "待排产", "已报价", "待排产", "未配送", "未申请", "待核销",
                "普通", new BigDecimal("420.00"), "报价已锁定，等待排产", List.of("海报", "加急")));
        add(new DemoOrder("ORD-1004", "社团负责人", "西区店", "待配送", "已报价", "已制作", "待配送接单", "未申请", "待核销-耗材",
                "普通", new BigDecimal("260.00"), "跨店路由完成，等待配送接单", List.of("跨店", "外协")));
        add(new DemoOrder("ORD-1005", "企业客户A", "市中心店", "已交付", "已报价", "已完成", "已签收", "待开票", "待日结",
                "普通", new BigDecimal("560.00"), "订单已交付，等待财务开票", List.of("企业", "发票")));
        add(new DemoOrder("ORD-1006", "设计工作室", "大学城店", "异常待处理", "已报价", "生产中", "未配送", "未申请", "挂起",
                "高", new BigDecimal("760.00"), "设备离线，等待总部或店长处理", List.of("异常", "设备离线")));
    }

    private void add(DemoOrder order) {
        orders.put(order.orderId, order);
    }

    private DemoOrder createCustomerOrder() {
        String orderId = "ORD-" + sequence.incrementAndGet();
        DemoOrder order = new DemoOrder(orderId, "张同学", "大学城店", "待接单", "未报价", "未排产", "未配送", "未申请", "待核销",
                "普通", new BigDecimal("96.00"), "客户新建订单，等待门店店员接单", new ArrayList<>(List.of("新订单", "自提")));
        orders.put(orderId, order);
        return order;
    }

    private List<DemoOrder> visibleOrders(String roleId) {
        return switch (roleId) {
            case "customer" -> orders.values().stream()
                    .filter(order -> order.customerName.equals("张同学") || order.orderId.equals("ORD-1001"))
                    .sorted(orderComparator())
                    .toList();
            case "clerk", "manager" -> orders.values().stream()
                    .filter(order -> order.storeName.equals("大学城店") || order.status.contains("异常") || order.quoteStatus.contains("审批"))
                    .sorted(orderComparator())
                    .toList();
            case "ops", "admin" -> orders.values().stream().sorted(orderComparator()).toList();
            case "finance" -> orders.values().stream()
                    .filter(order -> order.invoiceStatus.contains("开票") || order.financeStatus.contains("日结") || order.financeStatus.contains("核销"))
                    .sorted(orderComparator())
                    .toList();
            case "courier" -> orders.values().stream()
                    .filter(order -> order.deliveryStatus.contains("配送") || order.tags.contains("跨店") || order.tags.contains("配送异常"))
                    .sorted(orderComparator())
                    .toList();
            default -> List.of();
        };
    }

    private Comparator<DemoOrder> orderComparator() {
        return Comparator.comparing((DemoOrder order) -> order.priority.equals("高") ? 0 : 1)
                .thenComparing(order -> order.orderId);
    }

    private List<WorkbenchMetric> metrics(String roleId, List<DemoOrder> visibleOrders) {
        long exceptionCount = visibleOrders.stream().filter(order -> order.status.contains("异常") || order.tags.contains("异常")).count();
        return switch (roleId) {
            case "customer" -> List.of(
                    metric("我的订单", visibleOrders.size(), "normal"),
                    metric("待取件", count(visibleOrders, "待配送", "已制作"), "normal"),
                    metric("待开票", countInvoice(visibleOrders), "warn"));
            case "clerk" -> List.of(
                    metric("待接单", count(visibleOrders, "待接单"), "warn"),
                    metric("待报价", countQuote(visibleOrders), "normal"),
                    metric("异常单", exceptionCount, "warn"));
            case "manager" -> List.of(
                    metric("待审批", countQuoteApproval(visibleOrders), "warn"),
                    metric("生产中", count(visibleOrders, "生产中"), "normal"),
                    metric("异常接管", exceptionCount, "warn"));
            case "ops" -> List.of(
                    metric("跨店/外协", tagCount(visibleOrders, "跨店"), "normal"),
                    metric("SLA风险", exceptionCount, "warn"),
                    metric("门店数", 3, "normal"));
            case "finance" -> List.of(
                    metric("待开票", countInvoice(visibleOrders), "warn"),
                    metric("待核销", financeCount(visibleOrders, "待核销"), "normal"),
                    metric("挂起", financeCount(visibleOrders, "挂起"), "warn"));
            case "courier" -> List.of(
                    metric("待配送", deliveryCount(visibleOrders, "待配送"), "warn"),
                    metric("配送中", deliveryCount(visibleOrders, "配送已接单"), "normal"),
                    metric("异常", deliveryCount(visibleOrders, "配送异常"), "warn"));
            case "admin" -> List.of(
                    metric("审计事件", auditLogAppService.getAuditLogs(null, null).size(), "normal"),
                    metric("高危待确认", exceptionCount, "warn"),
                    metric("配置项", 6, "normal"));
            default -> List.of();
        };
    }

    private WorkbenchMetric metric(String label, long value, String tone) {
        return new WorkbenchMetric(label, Long.toString(value), tone);
    }

    private long count(List<DemoOrder> source, String... statuses) {
        return source.stream().filter(order -> containsAny(order.status, statuses) || containsAny(order.productionStatus, statuses)).count();
    }

    private long countQuote(List<DemoOrder> source) {
        return source.stream().filter(order -> order.quoteStatus.contains("报价")).count();
    }

    private long countQuoteApproval(List<DemoOrder> source) {
        return source.stream().filter(order -> order.quoteStatus.contains("审批")).count();
    }

    private long countInvoice(List<DemoOrder> source) {
        return source.stream().filter(order -> order.invoiceStatus.contains("开票") || order.invoiceStatus.contains("未申请")).count();
    }

    private long tagCount(List<DemoOrder> source, String tag) {
        return source.stream().filter(order -> order.tags.contains(tag)).count();
    }

    private long financeCount(List<DemoOrder> source, String status) {
        return source.stream().filter(order -> order.financeStatus.contains(status)).count();
    }

    private long deliveryCount(List<DemoOrder> source, String status) {
        return source.stream().filter(order -> order.deliveryStatus.contains(status)).count();
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private List<WorkbenchTask> tasks(String roleId, List<DemoOrder> visibleOrders) {
        List<WorkbenchTask> tasks = new ArrayList<>();
        for (DemoOrder order : visibleOrders) {
            switch (roleId) {
                case "customer" -> {
                    if (order.invoiceStatus.contains("未申请") || order.invoiceStatus.contains("待开票")) {
                        tasks.add(task(order, "申请或查看发票", "普通", "交付后1小时内", "customer_request_invoice"));
                    }
                    if (order.deliveryStatus.contains("已签收") && !order.status.contains("已取件")) {
                        tasks.add(task(order, "确认取件", "普通", "取件后即时", "customer_confirm_pickup"));
                    }
                }
                case "clerk" -> {
                    if (order.status.contains("待接单")) {
                        tasks.add(task(order, "核对文件并接单", "高", "3分钟内", "clerk_accept_order"));
                    }
                    if (order.quoteStatus.contains("待报价") || order.quoteStatus.contains("未报价")) {
                        tasks.add(task(order, "完成报价", "普通", "3分钟内", "clerk_quote_order"));
                    }
                    if (order.status.contains("异常")) {
                        tasks.add(task(order, "补充异常备注", "高", "2步内", "clerk_mark_exception"));
                    }
                }
                case "manager" -> {
                    if (order.quoteStatus.contains("审批")) {
                        tasks.add(task(order, "审批折扣申请", "高", "30秒内", "manager_approve_discount"));
                    }
                    if (order.status.contains("异常") || order.productionStatus.contains("待排产")) {
                        tasks.add(task(order, "人工排产干预", "高", "10分钟内", "manager_dispatch_override"));
                    }
                }
                case "ops" -> {
                    if (order.deliveryStatus.contains("未配送") || order.deliveryStatus.contains("待配送")) {
                        tasks.add(task(order, "生成跨店路由", "普通", "3分钟内", "ops_route_order"));
                    }
                }
                case "finance" -> {
                    if (order.invoiceStatus.contains("待开票")) {
                        tasks.add(task(order, "开具电子发票", "高", "1小时内", "finance_issue_invoice"));
                    }
                    if (!order.financeStatus.contains("已日结")) {
                        tasks.add(task(order, "日结核对", "普通", "闭店后30分钟", "finance_reconcile"));
                    }
                }
                case "courier" -> {
                    if (order.deliveryStatus.contains("待配送")) {
                        tasks.add(task(order, "接收配送任务", "高", "20分钟内", "courier_accept_delivery"));
                    }
                    if (order.deliveryStatus.contains("配送已接单")) {
                        tasks.add(task(order, "扫码签收", "普通", "到店后即时", "courier_sign_order"));
                    }
                }
                case "admin" -> {
                    if (order.status.contains("异常")) {
                        tasks.add(task(order, "查看高危审计", "高", "15分钟内", "admin_export_audit"));
                    }
                }
                default -> {
                }
            }
        }
        if (roleId.equals("customer")) {
            tasks.add(new WorkbenchTask("TASK-CREATE", "", "新建一笔图文快印订单", "普通", "随时", "customer_create_order"));
        }
        if (roleId.equals("ops")) {
            tasks.add(new WorkbenchTask("TASK-ROUTE-RULE", "", "调整跨店路由权重", "普通", "按需", "ops_update_rule"));
        }
        if (roleId.equals("admin")) {
            tasks.add(new WorkbenchTask("TASK-CONFIG", "", "保存权限配置快照", "普通", "变更后", "admin_update_config"));
        }
        return tasks.stream().limit(8).toList();
    }

    private WorkbenchTask task(DemoOrder order, String title, String severity, String dueText, String actionId) {
        return new WorkbenchTask("TASK-" + order.orderId + "-" + actionId, order.orderId, title, severity, dueText, actionId);
    }

    private List<RoleAction> actions(String roleId) {
        return switch (roleId) {
            case "customer" -> List.of(
                    action("customer_create_order", "新建订单", "ORD", false),
                    action("customer_request_invoice", "申请发票", "FIN", true),
                    action("customer_confirm_pickup", "确认取件", "DLV", true));
            case "clerk" -> List.of(
                    action("clerk_accept_order", "接单核对", "ORD", true),
                    action("clerk_quote_order", "完成报价", "QUO", true),
                    action("clerk_mark_exception", "异常备注", "ORD", true));
            case "manager" -> List.of(
                    action("manager_approve_discount", "批准折扣", "QUO", true),
                    action("manager_dispatch_override", "排产干预", "PRO", true));
            case "ops" -> List.of(
                    action("ops_route_order", "跨店派单", "DLV", true),
                    action("ops_update_rule", "更新路由规则", "AUD", false));
            case "finance" -> List.of(
                    action("finance_issue_invoice", "开具发票", "FIN", true),
                    action("finance_reconcile", "日结核对", "FIN", true));
            case "courier" -> List.of(
                    action("courier_accept_delivery", "配送接单", "DLV", true),
                    action("courier_sign_order", "扫码签收", "DLV", true),
                    action("courier_report_exception", "异常报备", "DLV", true));
            case "admin" -> List.of(
                    action("admin_export_audit", "导出审计", "AUD", false),
                    action("admin_update_config", "保存配置快照", "AUD", false));
            default -> List.of();
        };
    }

    private RoleAction action(String actionId, String label, String moduleCode, boolean orderRequired) {
        return new RoleAction(actionId, label, moduleCode, orderRequired);
    }

    private WorkbenchOrder toOrderView(DemoOrder order) {
        return new WorkbenchOrder(order.orderId, order.customerName, order.storeName, order.status, order.quoteStatus,
                order.productionStatus, order.deliveryStatus, order.invoiceStatus, order.financeStatus,
                order.priority, order.amount, order.currentStep, List.copyOf(order.tags));
    }

    private List<AuditLog> recentAudits() {
        List<AuditLog> logs = new ArrayList<>(auditLogAppService.getAuditLogs(null, null));
        logs.sort(Comparator.comparing(AuditLog::timestamp).reversed());
        return logs.stream().limit(8).toList();
    }

    private void update(DemoOrder order, String status, String quoteStatus, String productionStatus, String deliveryStatus,
            String invoiceStatus, String financeStatus, String currentStep) {
        order.status = status;
        order.quoteStatus = quoteStatus;
        order.productionStatus = productionStatus;
        order.deliveryStatus = deliveryStatus;
        order.invoiceStatus = invoiceStatus;
        order.financeStatus = financeStatus;
        order.currentStep = currentStep;
    }

    private void recordGlobal(RoleProfile role, RoleAction action, String snapshot) {
        auditRecorder.record(role.userId(), action.actionId(), snapshot);
    }

    private RoleProfile requireRole(String roleId) {
        RoleProfile role = roles.get(roleId);
        if (role == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "未知角色：" + roleId);
        }
        return role;
    }

    private RoleAction requireAction(String roleId, String actionId) {
        return actions(roleId).stream()
                .filter(action -> action.actionId().equals(actionId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "当前角色不允许执行该动作。"));
    }

    private DemoOrder requireOrder(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该动作必须选择订单。");
        }
        DemoOrder order = orders.get(orderId);
        if (order == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "订单不存在：" + orderId);
        }
        return order;
    }

    private BigDecimal amountFromPayload(Map<String, Object> payload, BigDecimal defaultAmount) {
        if (payload == null || !payload.containsKey("amount")) {
            return defaultAmount;
        }
        return new BigDecimal(String.valueOf(payload.get("amount")));
    }

    private static final class DemoOrder {
        private final String orderId;
        private final String customerName;
        private final String storeName;
        private String status;
        private String quoteStatus;
        private String productionStatus;
        private String deliveryStatus;
        private String invoiceStatus;
        private String financeStatus;
        private final String priority;
        private BigDecimal amount;
        private String currentStep;
        private final List<String> tags;

        private DemoOrder(String orderId, String customerName, String storeName, String status, String quoteStatus,
                String productionStatus, String deliveryStatus, String invoiceStatus, String financeStatus,
                String priority, BigDecimal amount, String currentStep, List<String> tags) {
            this.orderId = orderId;
            this.customerName = customerName;
            this.storeName = storeName;
            this.status = status;
            this.quoteStatus = quoteStatus;
            this.productionStatus = productionStatus;
            this.deliveryStatus = deliveryStatus;
            this.invoiceStatus = invoiceStatus;
            this.financeStatus = financeStatus;
            this.priority = priority;
            this.amount = amount;
            this.currentStep = currentStep;
            this.tags = new ArrayList<>(tags);
        }
    }
}
