package com.printshop.mis.dashboard;

import com.printshop.mis.domain.OrderChangeRequest;
import com.printshop.mis.domain.PrintOrder;
import com.printshop.mis.domain.UserAccount;
import com.printshop.mis.identity.IdentityService;
import com.printshop.mis.order.OrderChangeRequestService;
import com.printshop.mis.order.OrderService;
import com.printshop.mis.order.OrderWorkflowService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WorkbenchService {

    private final IdentityService identityService;
    private final OrderService orderService;
    private final OrderWorkflowService workflowService;
    private final OrderChangeRequestService changeRequestService;

    public WorkbenchService(
            IdentityService identityService,
            OrderService orderService,
            OrderWorkflowService workflowService,
            OrderChangeRequestService changeRequestService
    ) {
        this.identityService = identityService;
        this.orderService = orderService;
        this.workflowService = workflowService;
        this.changeRequestService = changeRequestService;
    }

    public Map<String, Object> tasks(String username) {
        UserAccount user = identityService.requireUser(username);
        List<PrintOrder> orders = orderService.visibleOrders(user);
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (PrintOrder order : orders) {
            for (Map<String, Object> action : workflowService.nextActions(username, order.id)) {
                tasks.add(orderTask(order, action));
            }
        }
        for (OrderChangeRequest change : changeRequestService.listChangeRequests(username)) {
            if ("PENDING".equals(change.status) && ("MANAGER".equals(user.role) || "ADMIN".equals(user.role))) {
                tasks.add(changeTask(change, "APPROVE_CHANGE", "审批订单变更"));
                tasks.add(changeTask(change, "REJECT_CHANGE", "驳回订单变更"));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", user);
        result.put("metrics", List.of(
                metric("待办任务", tasks.size()),
                metric("可见订单", orders.size()),
                metric("待审批变更", tasks.stream().filter(item -> "CHANGE_REQUEST".equals(item.get("type"))).count()),
                metric("最近订单", Math.min(orders.size(), 8))
        ));
        result.put("tasks", tasks);
        result.put("recentOrders", orders.stream().limit(8).toList());
        return result;
    }

    private Map<String, Object> orderTask(PrintOrder order, Map<String, Object> action) {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("id", "ORDER-" + order.id + "-" + action.get("action"));
        task.put("type", "ORDER");
        task.put("orderId", order.id);
        task.put("orderNo", order.orderNo);
        task.put("title", action.get("label"));
        task.put("action", action.get("action"));
        task.put("actionLabel", action.get("label"));
        task.put("hint", action.get("hint"));
        task.put("status", order.status);
        task.put("currentStep", order.currentStep);
        task.put("customerName", order.customerName);
        task.put("priority", order.priority);
        task.put("path", "/api/orders/" + order.id + "/workflow/actions/" + action.get("action"));
        return task;
    }

    private Map<String, Object> changeTask(OrderChangeRequest change, String action, String label) {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("id", "CHANGE-" + change.id + "-" + action);
        task.put("type", "CHANGE_REQUEST");
        task.put("orderId", change.orderId);
        task.put("changeRequestId", change.id);
        task.put("orderNo", change.orderNo);
        task.put("title", label);
        task.put("action", action);
        task.put("actionLabel", label);
        task.put("hint", change.reason);
        task.put("status", change.status);
        task.put("currentStep", "订单变更等待店长处理");
        task.put("customerName", change.requestedBy);
        task.put("priority", "高");
        task.put("path", "/api/order-change-requests/" + change.id + ("APPROVE_CHANGE".equals(action) ? "/approve" : "/reject"));
        return task;
    }

    private Map<String, Object> metric(String label, Object value) {
        return Map.of("label", label, "value", value);
    }
}
