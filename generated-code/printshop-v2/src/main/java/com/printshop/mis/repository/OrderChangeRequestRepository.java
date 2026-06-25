package com.printshop.mis.repository;

import com.printshop.mis.domain.OrderChangeRequest;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderChangeRequestRepository extends JpaRepository<OrderChangeRequest, Long> {
    List<OrderChangeRequest> findByOrderIdOrderByCreatedAtDesc(Long orderId);
    List<OrderChangeRequest> findByOrderIdAndStatusOrderByCreatedAtDesc(Long orderId, String status);
}
