package com.printshop.mis.reporting;

import com.printshop.mis.domain.InventoryItem;
import com.printshop.mis.domain.PaymentRecord;
import com.printshop.mis.domain.PrintOrder;
import com.printshop.mis.identity.IdentityService;
import com.printshop.mis.order.OrderService;
import com.printshop.mis.repository.InventoryItemRepository;
import com.printshop.mis.repository.InvoiceRecordRepository;
import com.printshop.mis.repository.JobTicketRepository;
import com.printshop.mis.repository.PaymentRecordRepository;
import com.printshop.mis.repository.PrintOrderRepository;
import com.printshop.mis.repository.ProductionTaskRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReportingService {

    private final PrintOrderRepository orders;
    private final IdentityService identityService;
    private final OrderService orderService;
    private final InvoiceRecordRepository invoices;
    private final PaymentRecordRepository payments;
    private final ProductionTaskRepository productionTasks;
    private final JobTicketRepository jobTickets;
    private final InventoryItemRepository inventoryItems;

    public ReportingService(
            PrintOrderRepository orders,
            IdentityService identityService,
            OrderService orderService,
            InvoiceRecordRepository invoices,
            PaymentRecordRepository payments,
            ProductionTaskRepository productionTasks,
            JobTicketRepository jobTickets,
            InventoryItemRepository inventoryItems
    ) {
        this.orders = orders;
        this.identityService = identityService;
        this.orderService = orderService;
        this.invoices = invoices;
        this.payments = payments;
        this.productionTasks = productionTasks;
        this.jobTickets = jobTickets;
        this.inventoryItems = inventoryItems;
    }

    public Map<String, Object> reports(String username) {
        var allOrders = orderService.visibleOrders(identityService.requireUser(username));
        var visibleOrderIds = allOrders.stream().map(order -> order.id).collect(java.util.stream.Collectors.toSet());
        var allPayments = payments.findAll().stream()
                .filter(payment -> visibleOrderIds.contains(payment.orderId))
                .toList();
        var allInvoices = invoices.findAll().stream()
                .filter(invoice -> visibleOrderIds.contains(invoice.orderId))
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderFunnel", orderFunnel(allOrders));
        result.put("finance", Map.of(
                "invoiceCount", allInvoices.size(),
                "issuedInvoiceCount", allInvoices.stream().filter(invoice -> "ISSUED".equals(invoice.status)).count(),
                "paymentCount", allPayments.size(),
                "paidAmount", sumPayments(allPayments, "SUCCESS"),
                "refundAmount", sumPayments(allPayments, "REFUNDED")
        ));
        result.put("operations", Map.of(
                "totalOrders", allOrders.size(),
                "completedOrders", allOrders.stream().filter(order -> "DONE".equals(order.status)).count(),
                "refundedOrders", allOrders.stream().filter(order -> "REFUNDED".equals(order.status)).count(),
                "activeOrders", allOrders.stream().filter(order -> !java.util.Set.of("DONE", "REFUNDED", "CANCELLED").contains(order.status)).count()
        ));
        result.put("storeSummary", allOrders.stream()
                .collect(Collectors.groupingBy(order -> order.storeName == null ? "未分配门店" : order.storeName,
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.toList(), this::storeStats))));
        result.put("productionLoad", productionTasks.findAll().stream()
                .filter(task -> jobTickets.findById(task.jobTicketId)
                        .map(ticket -> visibleOrderIds.contains(ticket.orderId))
                        .orElse(false))
                .toList());
        result.put("lowStock", inventoryItems.findAll().stream()
                .filter(this::isLowStock)
                .toList());
        return result;
    }

    private Map<String, Long> orderFunnel(java.util.List<PrintOrder> allOrders) {
        Map<String, Long> funnel = new LinkedHashMap<>();
        for (String status : java.util.List.of("SUBMITTED", "REVIEWING", "QUOTED", "JOB_READY", "IN_PRODUCTION", "PRODUCTION_DONE", "DELIVERING", "DONE", "REFUNDED", "CANCELLED")) {
            funnel.put(status, allOrders.stream().filter(order -> status.equals(order.status)).count());
        }
        return funnel;
    }

    private BigDecimal sumPayments(java.util.List<PaymentRecord> allPayments, String status) {
        return allPayments.stream()
                .filter(payment -> status.equals(payment.status))
                .map(payment -> payment.amount == null ? BigDecimal.ZERO : payment.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, Object> storeStats(java.util.List<PrintOrder> storeOrders) {
        return Map.of(
                "orderCount", storeOrders.size(),
                "completedCount", storeOrders.stream().filter(order -> "DONE".equals(order.status)).count(),
                "quotedAmount", storeOrders.stream()
                        .map(order -> order.totalAmount == null ? BigDecimal.ZERO : order.totalAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        );
    }

    private boolean isLowStock(InventoryItem item) {
        return item.quantity != null
                && item.safetyStock != null
                && item.quantity.compareTo(item.safetyStock) <= 0;
    }
}
