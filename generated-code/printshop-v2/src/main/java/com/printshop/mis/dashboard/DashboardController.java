package com.printshop.mis.dashboard;

import com.printshop.common.api.ApiResponse;
import com.printshop.mis.shared.ApiSupport;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {

    private final DashboardService dashboardService;
    private final ApiSupport api;

    public DashboardController(DashboardService dashboardService, ApiSupport api) {
        this.dashboardService = dashboardService;
        this.api = api;
    }

    @GetMapping("/api/me/dashboard")
    public ApiResponse<?> dashboard(Principal principal) {
        return api.ok(dashboardService.dashboard(principal.getName()));
    }
}
