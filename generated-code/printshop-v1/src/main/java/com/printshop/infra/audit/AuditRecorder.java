package com.printshop.infra.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

/**
 * v1 内存审计记录器。
 */
@Component
public class AuditRecorder {

    private final CopyOnWriteArrayList<AuditRecord> records = new CopyOnWriteArrayList<>();

    public void record(String action, String snapshot) {
        record("system", action, snapshot);
    }

    public void record(String operatorId, String action, String snapshot) {
        records.add(new AuditRecord(UUID.randomUUID().toString(), operatorId, action, snapshot, Instant.now()));
    }

    public List<AuditRecord> findAll() {
        return new ArrayList<>(records);
    }
}
