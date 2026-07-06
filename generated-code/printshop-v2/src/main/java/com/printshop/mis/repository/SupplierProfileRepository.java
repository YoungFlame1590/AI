package com.printshop.mis.repository;

import com.printshop.mis.domain.SupplierProfile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierProfileRepository extends JpaRepository<SupplierProfile, Long> {
    Optional<SupplierProfile> findFirstBySkuAndActiveTrueOrderByLeadTimeDaysAsc(String sku);
    List<SupplierProfile> findByActiveTrueOrderBySkuAsc();
}
