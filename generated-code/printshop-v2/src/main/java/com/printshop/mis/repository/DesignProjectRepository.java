package com.printshop.mis.repository;

import com.printshop.mis.domain.DesignProject;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DesignProjectRepository extends JpaRepository<DesignProject, Long> {
    List<DesignProject> findByCustomerIdOrderByUpdatedAtDesc(Long customerId);
    List<DesignProject> findByStoreIdOrderByUpdatedAtDesc(Long storeId);
    List<DesignProject> findAllByOrderByUpdatedAtDesc();
}
