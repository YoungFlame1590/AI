package com.printshop.mis.maintenance;

import com.printshop.common.api.ApiResponse;
import com.printshop.mis.shared.ApiSupport;
import java.security.Principal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class MaintenanceController {

    private final BusinessDataMaintenanceService maintenanceService;
    private final DemoTestDataService demoTestDataService;
    private final ApiSupport api;

    public MaintenanceController(BusinessDataMaintenanceService maintenanceService, DemoTestDataService demoTestDataService, ApiSupport api) {
        this.maintenanceService = maintenanceService;
        this.demoTestDataService = demoTestDataService;
        this.api = api;
    }

    @DeleteMapping("/api/admin/business-data")
    public ApiResponse<?> clearBusinessData(Principal principal) {
        return api.ok(maintenanceService.clearBusinessData(principal.getName()));
    }

    @PostMapping("/api/admin/demo-test")
    public ApiResponse<?> seedDemoTestData(Principal principal, @RequestBody(required = false) Map<String, Object> request) {
        return api.ok(demoTestDataService.seed(principal.getName(), request == null ? Map.of() : request));
    }
}
