package com.printshop.mis.reporting;

import com.printshop.mis.domain.InventoryItem;
import com.printshop.mis.repository.InventoryItemRepository;
import com.printshop.mis.repository.InvoiceRecordRepository;
import com.printshop.mis.repository.PaymentRecordRepository;
import com.printshop.mis.repository.PrintOrderRepository;
import com.printshop.mis.repository.ProductionTaskRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReportingService {

    private final PrintOrderRepository orders;
    private final InvoiceRecordRepository invoices;
    private final PaymentRecordRepository payments;
    private final ProductionTaskRepository productionTasks;
    private final InventoryItemRepository inventoryItems;

    public ReportingService(
            PrintOrderRepository orders,
            InvoiceRecordRepository invoices,
            PaymentRecordRepository payments,
            ProductionTaskRepository productionTasks,
            InventoryItemRepository inventoryItems
    ) {
        this.orders = orders;
        this.invoices = invoices;
        this.payments = payments;
        this.productionTasks = productionTasks;
        this.inventoryItems = inventoryItems;
    }

    public Map<String, Object> reports() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderFunnel", Map.of(
                "submitted", countOrders("SUBMITTED"),
                "quoted", countOrders("QUOTED"),
                "jobReady", countOrders("JOB_READY"),
                "production", countOrders("IN_PRODUCTION")
        ));
        result.put("finance", Map.of(
                "invoiceCount", invoices.count(),
                "paymentCount", payments.count()
        ));
        result.put("productionLoad", productionTasks.findAll());
        result.put("lowStock", inventoryItems.findAll().stream()
                .filter(this::isLowStock)
                .toList());
        return result;
    }

    private long countOrders(String status) {
        return orders.findAll().stream()
                .filter(order -> status.equals(order.status))
                .count();
    }

    private boolean isLowStock(InventoryItem item) {
        return item.quantity != null
                && item.safetyStock != null
                && item.quantity.compareTo(item.safetyStock) <= 0;
    }
}
