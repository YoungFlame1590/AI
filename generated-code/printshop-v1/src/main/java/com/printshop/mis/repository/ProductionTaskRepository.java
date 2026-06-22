package com.printshop.mis.repository;

import com.printshop.mis.domain.ProductionTask;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductionTaskRepository extends JpaRepository<ProductionTask, Long> {
    List<ProductionTask> findByStatusOrderByPlannedEndAsc(String status);
}
