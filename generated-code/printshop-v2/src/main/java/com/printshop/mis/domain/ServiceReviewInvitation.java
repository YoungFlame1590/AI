package com.printshop.mis.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "service_review_invitations")
public class ServiceReviewInvitation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long orderId;
    public String orderNo;
    public Long customerId;
    public String customerName;
    public Long storeId;
    public String status;
    public LocalDateTime invitedAt;
    public LocalDateTime respondedAt;
}
