package com.printshop.mis.repository;

import com.printshop.mis.domain.PrintOrder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrintOrderRepository extends JpaRepository<PrintOrder, Long> {
    Optional<PrintOrder> findByOrderNo(String orderNo);
    List<PrintOrder> findByCustomerIdOrderByUpdatedAtDesc(Long customerId);
    List<PrintOrder> findTop8ByOrderByUpdatedAtDesc();
}
