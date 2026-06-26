package com.printshop.mis.repository;

import com.printshop.mis.domain.Store;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, Long> {
    List<Store> findByActiveTrueOrderByNameAsc();
    Optional<Store> findByCode(String code);
    boolean existsByCode(String code);
}
