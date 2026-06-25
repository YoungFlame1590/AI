package com.printshop.mis.repository;

import com.printshop.mis.domain.InvoiceRecord;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRecordRepository extends JpaRepository<InvoiceRecord, Long> {
    List<InvoiceRecord> findByOrderId(Long orderId);

    List<InvoiceRecord> findByOrderIdAndStatusIn(Long orderId, Collection<String> statuses);
}
