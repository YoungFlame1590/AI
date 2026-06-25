package com.printshop.mis.order;

import com.printshop.common.api.ApiResponse;
import com.printshop.mis.shared.ApiSupport;
import java.security.Principal;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderChangeRequestController {

    private final OrderChangeRequestService changeRequestService;
    private final ApiSupport api;

    public OrderChangeRequestController(OrderChangeRequestService changeRequestService, ApiSupport api) {
        this.changeRequestService = changeRequestService;
        this.api = api;
    }

    @GetMapping("/api/order-change-requests")
    public ApiResponse<?> changeRequests(Principal principal) {
        return api.ok(changeRequestService.listChangeRequests(principal.getName()));
    }

    @GetMapping("/api/order-change-requests/{id}")
    public ApiResponse<?> changeRequest(Principal principal, @PathVariable Long id) {
        return api.ok(changeRequestService.getChangeRequest(principal.getName(), id));
    }

    @PostMapping("/api/orders/{orderId}/change-requests")
    public ApiResponse<?> createChangeRequest(Principal principal, @PathVariable Long orderId, @RequestBody Map<String, Object> payload) {
        return api.ok(changeRequestService.createChangeRequest(principal.getName(), orderId, payload));
    }

    @PostMapping("/api/order-change-requests/{id}/approve")
    public ApiResponse<?> approveChangeRequest(Principal principal, @PathVariable Long id, @RequestBody(required = false) Map<String, Object> payload) {
        return api.ok(changeRequestService.approveChangeRequest(principal.getName(), id, payload == null ? Map.of() : payload));
    }

    @PostMapping("/api/order-change-requests/{id}/reject")
    public ApiResponse<?> rejectChangeRequest(Principal principal, @PathVariable Long id, @RequestBody(required = false) Map<String, Object> payload) {
        return api.ok(changeRequestService.rejectChangeRequest(principal.getName(), id, payload == null ? Map.of() : payload));
    }
}
