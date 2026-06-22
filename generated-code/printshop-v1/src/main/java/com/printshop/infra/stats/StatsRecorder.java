package com.printshop.infra.stats;

import com.printshop.common.api.StatsResponse;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * v1 内存统计组件，进程重启后清零。
 */
@Component
public class StatsRecorder {

    private final AtomicLong totalRequests = new AtomicLong();
    private final ConcurrentHashMap<String, AtomicLong> moduleCounts = new ConcurrentHashMap<>();
    private volatile Instant lastRequestAt;

    public void record(String moduleCode) {
        totalRequests.incrementAndGet();
        moduleCounts.computeIfAbsent(moduleCode, ignored -> new AtomicLong()).incrementAndGet();
        lastRequestAt = Instant.now();
    }

    public StatsResponse snapshot() {
        Map<String, Long> counts = moduleCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().get(),
                        (left, right) -> left,
                        java.util.LinkedHashMap::new
                ));
        return new StatsResponse(totalRequests.get(), counts, lastRequestAt);
    }
}
