package com.printshop.mis.repository;

import com.printshop.mis.domain.Quotation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {
    List<Quotation> findByOrderIdOrderByCreatedAtDesc(Long orderId);
}
