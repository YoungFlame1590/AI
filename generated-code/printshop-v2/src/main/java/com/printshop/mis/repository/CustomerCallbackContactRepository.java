package com.printshop.mis.repository;

import com.printshop.mis.domain.CustomerCallbackContact;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerCallbackContactRepository extends JpaRepository<CustomerCallbackContact, Long> {
    boolean existsByCustomerIdAndStoreIdAndContactedAtAfter(Long customerId, Long storeId, LocalDateTime contactedAt);
}
