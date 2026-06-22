package com.printshop.aud.application;

import com.printshop.aud.dto.AuditLog;
import com.printshop.infra.audit.AuditRecord;
import com.printshop.infra.audit.AuditRecorder;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 审计查询应用服务。
 * 职责：读取审计快照并按查询条件过滤。
 *
 * @see REQ-AUD-001
 */
@Service
public class AuditLogAppService {

    private final AuditRecorder auditRecorder;

    public AuditLogAppService(AuditRecorder auditRecorder) {
        this.auditRecorder = auditRecorder;
    }

    public List<AuditLog> getAuditLogs(String orderId, String operatorId) {
        return auditRecorder.findAll().stream()
                .filter(record -> !hasText(orderId) || record.snapshot().contains(orderId))
                .filter(record -> !hasText(operatorId) || record.operatorId().equals(operatorId))
                .map(this::toDto)
                .toList();
    }

    private AuditLog toDto(AuditRecord record) {
        return new AuditLog(
                record.logId(),
                record.operatorId(),
                record.action(),
                record.snapshot(),
                record.timestamp().toString()
        );
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
