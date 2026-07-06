package com.printshop.mis.dashboard;

import com.printshop.mis.domain.OrderChangeRequest;
import com.printshop.mis.domain.PrintOrder;
import com.printshop.mis.domain.ComplaintTicket;
import com.printshop.mis.domain.ServiceReviewInvitation;
import com.printshop.mis.domain.UserAccount;
import com.printshop.mis.identity.IdentityService;
import com.printshop.mis.order.OrderChangeRequestService;
import com.printshop.mis.order.OrderService;
import com.printshop.mis.order.OrderWorkflowService;
import com.printshop.mis.repository.ComplaintTicketRepository;
import com.printshop.mis.repository.ServiceReviewInvitationRepository;
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
    private final ServiceReviewInvitationRepository reviewInvitations;
    private final ComplaintTicketRepository complaintTickets;

    public WorkbenchService(
            IdentityService identityService,
            OrderService orderService,
            OrderWorkflowService workflowService,
            OrderChangeRequestService changeRequestService,
            ServiceReviewInvitationRepository reviewInvitations,
            ComplaintTicketRepository complaintTickets
    ) {
        this.identityService = identityService;
        this.orderService = orderService;
        this.workflowService = workflowService;
        this.changeRequestService = changeRequestService;
        this.reviewInvitations = reviewInvitations;
        this.complaintTickets = complaintTickets;
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
        if ("CUSTOMER".equals(user.role)) {
            for (ServiceReviewInvitation invitation : reviewInvitations.findByCustomerIdOrderByInvitedAtDesc(user.id)) {
                if ("PENDING".equals(invitation.status)) {
                    tasks.add(reviewTask(invitation));
                }
            }
        }
        if ("MANAGER".equals(user.role) || "ADMIN".equals(user.role)) {
            List<ComplaintTicket> visibleComplaints = "ADMIN".equals(user.role)
                    ? complaintTickets.findAllByOrderByCreatedAtDesc()
                    : complaintTickets.findByStoreIdOrderByCreatedAtDesc(user.storeId);
            for (ComplaintTicket complaint : visibleComplaints) {
                if (!"CLOSED".equals(complaint.status)) {
                    tasks.add(complaintTask(complaint));
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", user);
        result.put("metrics", List.of(
                metric("待办任务", tasks.size()),
                metric("可见订单", orders.size()),
                metric("待审批变更", tasks.stream().filter(item -> "CHANGE_REQUEST".equals(item.get("type"))).count()),
                metric("待评价/客诉", tasks.stream().filter(item -> "SERVICE_REVIEW".equals(item.get("type")) || "COMPLAINT".equals(item.get("type"))).count()),
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

    private Map<String, Object> reviewTask(ServiceReviewInvitation invitation) {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("id", "REVIEW-" + invitation.id);
        task.put("type", "SERVICE_REVIEW");
        task.put("orderId", invitation.orderId);
        task.put("orderNo", invitation.orderNo);
        task.put("title", "评价本次服务");
        task.put("action", "SUBMIT_SERVICE_REVIEW");
        task.put("actionLabel", "提交评价");
        task.put("hint", "订单已完成，请对印刷质量、准时性、服务态度和性价比评分。");
        task.put("status", invitation.status);
        task.put("currentStep", "等待客户评价");
        task.put("customerName", invitation.customerName);
        task.put("priority", "普通");
        task.put("path", "/api/orders/" + invitation.orderId + "/service-reviews");
        return task;
    }

    private Map<String, Object> complaintTask(ComplaintTicket complaint) {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("id", "COMPLAINT-" + complaint.id);
        task.put("type", "COMPLAINT");
        task.put("orderId", complaint.orderId);
        task.put("complaintId", complaint.id);
        task.put("orderNo", complaint.orderNo);
        task.put("title", "处理客户差评工单");
        task.put("action", "REPLY_COMPLAINT");
        task.put("actionLabel", "回复客诉");
        task.put("hint", complaint.customerComment);
        task.put("status", complaint.status);
        task.put("currentStep", "差评客诉需24小时内回复");
        task.put("customerName", complaint.customerName);
        task.put("priority", "HIGH".equals(complaint.severity) ? "高" : "中");
        task.put("path", "/api/complaint-tickets/" + complaint.id + "/reply");
        return task;
    }

    private Map<String, Object> metric(String label, Object value) {
        return Map.of("label", label, "value", value);
    }
}
