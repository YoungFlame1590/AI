package com.printshop.mis.audit;

import com.printshop.common.api.ApiResponse;
import com.printshop.mis.shared.ApiSupport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuditController {

    private final AuditTrailService auditTrailService;
    private final ApiSupport api;

    public AuditController(AuditTrailService auditTrailService, ApiSupport api) {
        this.auditTrailService = auditTrailService;
        this.api = api;
    }

    @GetMapping("/api/audit-logs")
    public ApiResponse<?> auditLogs() {
        return api.ok(auditTrailService.auditLogs());
    }

    @GetMapping("/api/audit-logs/{id}")
    public ApiResponse<?> auditLog(@PathVariable Long id) {
        return api.ok(auditTrailService.getAuditLog(id));
    }
}
