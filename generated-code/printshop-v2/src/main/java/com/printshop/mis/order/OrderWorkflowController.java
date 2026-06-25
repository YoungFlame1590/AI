package com.printshop.mis.order;

import com.printshop.common.api.ApiResponse;
import com.printshop.mis.shared.ApiSupport;
import java.security.Principal;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderWorkflowController {

    private final OrderWorkflowService workflowService;
    private final ApiSupport api;

    public OrderWorkflowController(OrderWorkflowService workflowService, ApiSupport api) {
        this.workflowService = workflowService;
        this.api = api;
    }

    @PostMapping("/api/orders/{id}/workflow/quote")
    public ApiResponse<?> quickQuote(Principal principal, @PathVariable Long id) {
        return api.ok(workflowService.quickQuote(principal.getName(), id));
    }

    @PostMapping("/api/orders/{orderId}/workflow/actions/{action}")
    public ApiResponse<?> runWorkflowAction(
            Principal principal,
            @PathVariable("orderId") Long orderId,
            @PathVariable String action,
            @RequestBody(required = false) Map<String, Object> payload
    ) {
        return api.ok(workflowService.executeAction(principal.getName(), orderId, action, payload == null ? Map.of() : payload));
    }

    @PostMapping("/api/orders/{id}/workflow/job-ticket")
    public ApiResponse<?> quickJobTicket(Principal principal, @PathVariable Long id) {
        return api.ok(workflowService.quickJobTicket(principal.getName(), id));
    }

    @PostMapping("/api/orders/{id}/workflow/production-task")
    public ApiResponse<?> quickProductionTask(Principal principal, @PathVariable Long id) {
        return api.ok(workflowService.quickProductionTask(principal.getName(), id));
    }

    @PostMapping("/api/orders/{id}/workflow/delivery-task")
    public ApiResponse<?> quickDeliveryTask(Principal principal, @PathVariable Long id) {
        return api.ok(workflowService.quickDeliveryTask(principal.getName(), id));
    }

    @PostMapping("/api/orders/{id}/workflow/accept-delivery")
    public ApiResponse<?> acceptDelivery(Principal principal, @PathVariable Long id) {
        return api.ok(workflowService.acceptDelivery(principal.getName(), id));
    }

    @PostMapping("/api/orders/{id}/workflow/payment")
    public ApiResponse<?> quickPayment(Principal principal, @PathVariable Long id) {
        return api.ok(workflowService.quickPayment(principal.getName(), id));
    }

    @PostMapping("/api/orders/{id}/workflow/refund")
    public ApiResponse<?> quickRefund(Principal principal, @PathVariable Long id) {
        return api.ok(workflowService.quickRefund(principal.getName(), id));
    }

    @PostMapping("/api/orders/{id}/workflow/invoice")
    public ApiResponse<?> quickInvoice(Principal principal, @PathVariable Long id) {
        return api.ok(workflowService.quickInvoice(principal.getName(), id));
    }

    @PostMapping("/api/orders/{id}/workflow/full")
    public ApiResponse<?> quickFullFlow(Principal principal, @PathVariable Long id) {
        return api.ok(workflowService.quickFullFlow(principal.getName(), id));
    }
}
