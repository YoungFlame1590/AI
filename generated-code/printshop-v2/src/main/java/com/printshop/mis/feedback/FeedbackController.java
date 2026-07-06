package com.printshop.mis.feedback;

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
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final ApiSupport api;

    public FeedbackController(FeedbackService feedbackService, ApiSupport api) {
        this.feedbackService = feedbackService;
        this.api = api;
    }

    @GetMapping("/api/service-review-invitations")
    public ApiResponse<?> invitations(Principal principal) {
        return api.ok(feedbackService.invitations(principal.getName()));
    }

    @PostMapping("/api/orders/{orderId}/service-reviews")
    public ApiResponse<?> submitReview(Principal principal, @PathVariable Long orderId, @RequestBody Map<String, Object> request) {
        return api.ok(feedbackService.submitReview(principal.getName(), orderId, request));
    }

    @GetMapping("/api/service-reviews")
    public ApiResponse<?> reviews(Principal principal) {
        return api.ok(feedbackService.serviceReviews(principal.getName()));
    }

    @GetMapping("/api/complaint-tickets")
    public ApiResponse<?> complaints(Principal principal) {
        return api.ok(feedbackService.complaintTickets(principal.getName()));
    }

    @PostMapping("/api/complaint-tickets/{id}/reply")
    public ApiResponse<?> replyComplaint(Principal principal, @PathVariable Long id, @RequestBody Map<String, Object> request) {
        return api.ok(feedbackService.replyComplaint(principal.getName(), id, request));
    }

    @PostMapping("/api/complaint-tickets/{id}/close")
    public ApiResponse<?> closeComplaint(Principal principal, @PathVariable Long id) {
        return api.ok(feedbackService.closeComplaint(principal.getName(), id));
    }

    @GetMapping("/api/customer-callback-reminders")
    public ApiResponse<?> callbackReminders(Principal principal) {
        return api.ok(feedbackService.callbackReminders(principal.getName()));
    }
}
