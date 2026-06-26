package com.printshop.mis.repository;

import com.printshop.mis.domain.OrderFile;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderFileRepository extends JpaRepository<OrderFile, Long> {
    List<OrderFile> findByOrderIdOrderByUploadedAtDesc(Long orderId);
    long countByOrderId(Long orderId);
}
