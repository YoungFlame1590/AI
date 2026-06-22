package com.printshop.infra.audit;

import java.time.Instant;

/**
 * INFRA 内部审计记录，不依赖业务模块。
 */
public record AuditRecord(String logId, String operatorId, String action, String snapshot, Instant timestamp) {
}
