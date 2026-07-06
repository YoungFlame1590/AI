package com.printshop.mis.repository;

import com.printshop.mis.domain.ServiceReviewInvitation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceReviewInvitationRepository extends JpaRepository<ServiceReviewInvitation, Long> {
    List<ServiceReviewInvitation> findByCustomerIdOrderByInvitedAtDesc(Long customerId);
    Optional<ServiceReviewInvitation> findByOrderId(Long orderId);
    boolean existsByOrderId(Long orderId);
}
