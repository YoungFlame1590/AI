package com.printshop.mis.repository;

import com.printshop.mis.domain.ComplaintTicket;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplaintTicketRepository extends JpaRepository<ComplaintTicket, Long> {
    List<ComplaintTicket> findByStoreIdOrderByCreatedAtDesc(Long storeId);
    List<ComplaintTicket> findAllByOrderByCreatedAtDesc();
}
