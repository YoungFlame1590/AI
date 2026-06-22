package com.printshop.dlv.repository;

import com.printshop.dlv.dto.DeliveryTask;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * DLV 模块 v1 内存仓储。
 */
@Repository
public class InMemoryDeliveryTaskRepository {

    private final ConcurrentHashMap<String, DeliveryTask> tasks = new ConcurrentHashMap<>();

    public DeliveryTask save(DeliveryTask task) {
        tasks.put(task.taskId(), task);
        return task;
    }

    public Optional<DeliveryTask> findById(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }
}
