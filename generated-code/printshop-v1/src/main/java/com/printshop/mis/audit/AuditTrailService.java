package com.printshop.mis.audit;

import static com.printshop.mis.shared.MisSupport.notFound;
import static com.printshop.mis.shared.MisSupport.now;

import com.printshop.infra.stats.StatsRecorder;
import com.printshop.mis.domain.AuditLogEntry;
import com.printshop.mis.domain.UserAccount;
import com.printshop.mis.repository.AuditLogEntryRepository;
import com.printshop.mis.repository.UserAccountRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuditTrailService {

    private final UserAccountRepository users;
    private final AuditLogEntryRepository auditLogs;
    private final StatsRecorder stats;

    public AuditTrailService(UserAccountRepository users, AuditLogEntryRepository auditLogs, StatsRecorder stats) {
        this.users = users;
        this.auditLogs = auditLogs;
        this.stats = stats;
    }

    public void record(String username, String moduleCode, String action, String targetType, Object targetId, String detail) {
        UserAccount user = username == null ? null : users.findByUsername(username).orElse(null);
        AuditLogEntry entry = new AuditLogEntry();
        entry.operator = user == null ? "system" : user.displayName;
        entry.role = user == null ? "ADMIN" : user.role;
        entry.action = action;
        entry.targetType = targetType;
        entry.targetId = targetId == null ? "-" : String.valueOf(targetId);
        entry.detail = detail;
        entry.createdAt = now();
        auditLogs.save(entry);
        stats.record(moduleCode);
    }

    public List<AuditLogEntry> auditLogs() {
        return auditLogs.findTop20ByOrderByCreatedAtDesc().stream()
                .filter(entry -> !"SEED_DATA".equals(entry.action))
                .toList();
    }

    public AuditLogEntry getAuditLog(Long id) {
        return auditLogs.findById(id).orElseThrow(() -> notFound("审计日志", id));
    }
}
