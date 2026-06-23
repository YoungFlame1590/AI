package com.printshop.mis.maintenance;

import com.printshop.common.api.ApiResponse;
import com.printshop.mis.shared.ApiSupport;
import java.security.Principal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MaintenanceController {

    private final BusinessDataMaintenanceService maintenanceService;
    private final ApiSupport api;

    public MaintenanceController(BusinessDataMaintenanceService maintenanceService, ApiSupport api) {
        this.maintenanceService = maintenanceService;
        this.api = api;
    }

    @DeleteMapping("/api/admin/business-data")
    public ApiResponse<?> clearBusinessData(Principal principal) {
        return api.ok(maintenanceService.clearBusinessData(principal.getName()));
    }
}
