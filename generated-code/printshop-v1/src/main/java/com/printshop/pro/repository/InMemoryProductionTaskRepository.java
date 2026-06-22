package com.printshop.pro.repository;

import com.printshop.pro.dto.ProductionTask;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * PRO 模块 v1 内存仓储。
 */
@Repository
public class InMemoryProductionTaskRepository {

    private final ConcurrentHashMap<String, ProductionTask> tasks = new ConcurrentHashMap<>();

    public ProductionTask save(ProductionTask task) {
        tasks.put(task.taskId(), task);
        return task;
    }

    public Optional<ProductionTask> findById(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }
}
