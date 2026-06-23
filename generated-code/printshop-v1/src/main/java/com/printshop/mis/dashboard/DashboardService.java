package com.printshop.mis.dashboard;

import static com.printshop.mis.shared.MisSupport.metric;

import com.printshop.mis.domain.InventoryItem;
import com.printshop.mis.domain.PrintOrder;
import com.printshop.mis.domain.UserAccount;
import com.printshop.mis.identity.IdentityService;
import com.printshop.mis.order.OrderService;
import com.printshop.mis.repository.InventoryItemRepository;
import com.printshop.mis.repository.ProductionTaskRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final IdentityService identityService;
    private final OrderService orderService;
    private final ProductionTaskRepository productionTasks;
    private final InventoryItemRepository inventoryItems;

    public DashboardService(
            IdentityService identityService,
            OrderService orderService,
            ProductionTaskRepository productionTasks,
            InventoryItemRepository inventoryItems
    ) {
        this.identityService = identityService;
        this.orderService = orderService;
        this.productionTasks = productionTasks;
        this.inventoryItems = inventoryItems;
    }

    public Map<String, Object> dashboard(String username) {
        UserAccount user = identityService.requireUser(username);
        List<PrintOrder> visibleOrders = orderService.visibleOrders(user);
        List<InventoryItem> lowStock = lowStockItems();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", identityService.userView(user));
        result.put("metrics", List.of(
                metric("订单总数", visibleOrders.size()),
                metric("待报价/审批", countOrders(visibleOrders, "SUBMITTED", "QUOTED")),
                metric("生产/配送中", countOrders(visibleOrders, "IN_PRODUCTION", "DELIVERING")),
                metric("库存预警", lowStock.size())
        ));
        result.put("orders", visibleOrders.stream().limit(8).toList());
        result.put("productionBoard", productionTasks.findAll());
        result.put("lowStock", lowStock);
        return result;
    }

    private List<InventoryItem> lowStockItems() {
        return inventoryItems.findAll().stream()
                .filter(item -> item.quantity != null
                        && item.safetyStock != null
                        && item.quantity.compareTo(item.safetyStock) <= 0)
                .toList();
    }

    private long countOrders(List<PrintOrder> orders, String... statuses) {
        return orders.stream()
                .filter(order -> java.util.Arrays.asList(statuses).contains(order.status))
                .count();
    }
}
