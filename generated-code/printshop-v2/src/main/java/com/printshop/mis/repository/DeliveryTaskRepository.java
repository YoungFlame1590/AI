package com.printshop.mis.repository;

import com.printshop.mis.domain.DeliveryTask;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryTaskRepository extends JpaRepository<DeliveryTask, Long> {
    List<DeliveryTask> findByStatusOrderByUpdatedAtDesc(String status);
    List<DeliveryTask> findByOrderId(Long orderId);
}
