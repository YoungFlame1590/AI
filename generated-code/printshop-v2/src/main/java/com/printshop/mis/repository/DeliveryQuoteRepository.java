package com.printshop.mis.repository;

import com.printshop.mis.domain.DeliveryQuote;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryQuoteRepository extends JpaRepository<DeliveryQuote, Long> {
    List<DeliveryQuote> findByOrderIdOrderByCreatedAtDesc(Long orderId);
}
