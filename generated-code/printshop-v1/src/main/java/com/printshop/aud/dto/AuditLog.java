package com.printshop.aud.dto;

/**
 * 审计日志 DTO。
 *
 * @see REQ-AUD-001
 */
public record AuditLog(
        String logId,
        String operatorId,
        String action,
        String snapshot,
        String timestamp
) {
}
