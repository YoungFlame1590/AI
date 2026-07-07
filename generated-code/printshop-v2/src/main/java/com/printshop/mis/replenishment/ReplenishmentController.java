package com.printshop.mis.replenishment;

import com.printshop.common.api.ApiResponse;
import com.printshop.mis.shared.ApiSupport;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReplenishmentController {

    private final ReplenishmentService replenishmentService;
    private final ApiSupport api;

    public ReplenishmentController(ReplenishmentService replenishmentService, ApiSupport api) {
        this.replenishmentService = replenishmentService;
        this.api = api;
    }

    @GetMapping("/api/replenishment/recommendations")
    public ApiResponse<?> recommendations(Principal principal) {
        return api.ok(replenishmentService.recommendations(principal.getName()));
    }

    @GetMapping("/api/replenishment/forecast")
    public ApiResponse<?> forecast(Principal principal) {
        return api.ok(replenishmentService.forecastNextThirtyDays(principal.getName()));
    }

    @PostMapping("/api/replenishment/recalculate")
    public ApiResponse<?> recalculate(Principal principal) {
        return api.ok(replenishmentService.recalculate(principal.getName()));
    }

    @GetMapping("/api/purchase-suggestions")
    public ApiResponse<?> purchaseSuggestions(Principal principal) {
        return api.ok(replenishmentService.purchaseSuggestions(principal.getName()));
    }

    @PostMapping("/api/purchase-suggestions/{id}/approve")
    public ApiResponse<?> approveSuggestion(Principal principal, @PathVariable Long id) {
        return api.ok(replenishmentService.approveSuggestion(principal.getName(), id));
    }
}
