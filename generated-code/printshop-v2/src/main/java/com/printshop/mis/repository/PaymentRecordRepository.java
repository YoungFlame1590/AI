package com.printshop.mis.repository;

import com.printshop.mis.domain.PaymentRecord;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {
    List<PaymentRecord> findByOrderId(Long orderId);

    List<PaymentRecord> findByOrderIdAndStatusIn(Long orderId, Collection<String> statuses);
}
