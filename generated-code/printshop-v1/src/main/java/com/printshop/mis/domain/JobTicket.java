package com.printshop.mis.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_tickets")
public class JobTicket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String ticketNo;
    public Long orderId;
    public Long quotationId;
    public String specs;
    public String paperType;
    public String binding;
    public String status;
    public LocalDateTime createdAt;
}
