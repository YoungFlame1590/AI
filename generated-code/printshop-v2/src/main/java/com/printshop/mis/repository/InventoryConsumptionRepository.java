package com.printshop.mis.repository;

import com.printshop.mis.domain.InventoryConsumption;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryConsumptionRepository extends JpaRepository<InventoryConsumption, Long> {
    List<InventoryConsumption> findBySkuAndConsumedAtAfter(String sku, LocalDateTime consumedAt);
}
