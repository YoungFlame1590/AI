package com.printshop.aud.controller;

import com.printshop.aud.application.AuditLogAppService;
import com.printshop.aud.dto.AuditLog;
import com.printshop.infra.stats.StatsRecorder;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AUD Controller，仅负责 HTTP 契约适配。
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogAppService auditLogAppService;
    private final StatsRecorder statsRecorder;

    public AuditLogController(AuditLogAppService auditLogAppService, StatsRecorder statsRecorder) {
        this.auditLogAppService = auditLogAppService;
        this.statsRecorder = statsRecorder;
    }

    @Operation(operationId = "getAuditLogs", summary = "查询全量审计日志")
    @GetMapping
    public List<AuditLog> getAuditLogs(
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) String operatorId
    ) {
        statsRecorder.record("AUD");
        return auditLogAppService.getAuditLogs(orderId, operatorId);
    }
}
