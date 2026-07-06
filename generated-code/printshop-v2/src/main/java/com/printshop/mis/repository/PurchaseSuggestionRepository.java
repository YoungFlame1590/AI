package com.printshop.mis.repository;

import com.printshop.mis.domain.PurchaseSuggestion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseSuggestionRepository extends JpaRepository<PurchaseSuggestion, Long> {
    List<PurchaseSuggestion> findAllByOrderByCreatedAtDesc();
}
