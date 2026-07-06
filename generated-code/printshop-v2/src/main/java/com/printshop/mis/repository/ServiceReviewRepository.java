package com.printshop.mis.repository;

import com.printshop.mis.domain.ServiceReview;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceReviewRepository extends JpaRepository<ServiceReview, Long> {
    List<ServiceReview> findByStoreId(Long storeId);
    List<ServiceReview> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    boolean existsByOrderId(Long orderId);
}
