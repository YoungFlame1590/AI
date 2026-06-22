package com.printshop.mis.repository;

import com.printshop.mis.domain.AuditLogEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogEntryRepository extends JpaRepository<AuditLogEntry, Long> {
    List<AuditLogEntry> findTop20ByOrderByCreatedAtDesc();
}
