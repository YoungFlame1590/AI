package com.printshop.mis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "complaint_tickets")
public class ComplaintTicket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String ticketNo;
    public Long reviewId;
    public Long orderId;
    public String orderNo;
    public String customerName;
    public Long storeId;
    public String status;
    public String severity;
    @Column(length = 1000)
    public String customerComment;
    @Column(length = 1000)
    public String managerReply;
    public String repliedBy;
    public LocalDateTime createdAt;
    public LocalDateTime repliedAt;
    public LocalDateTime closedAt;
}
