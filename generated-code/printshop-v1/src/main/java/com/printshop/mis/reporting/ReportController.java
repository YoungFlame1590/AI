package com.printshop.mis.reporting;

import com.printshop.common.api.ApiResponse;
import com.printshop.mis.shared.ApiSupport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReportController {

    private final ReportingService reportingService;
    private final ApiSupport api;

    public ReportController(ReportingService reportingService, ApiSupport api) {
        this.reportingService = reportingService;
        this.api = api;
    }

    @GetMapping("/api/reports")
    public ApiResponse<?> reports() {
        return api.ok(reportingService.reports());
    }
}
