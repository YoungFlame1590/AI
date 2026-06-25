package com.printshop.mis.repository;

import com.printshop.mis.domain.JobTicket;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobTicketRepository extends JpaRepository<JobTicket, Long> {
    List<JobTicket> findByOrderIdOrderByCreatedAtDesc(Long orderId);
}
