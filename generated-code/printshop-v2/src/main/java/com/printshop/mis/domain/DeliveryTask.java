package com.printshop.mis.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "delivery_tasks")
public class DeliveryTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String taskNo;
    public Long orderId;
    public String mode;
    public String carrierName;
    public String carrierUsername;
    public String targetStore;
    public String status;
    public String signedBy;
    public String deliveryChannel;
    public String trackingNo;
    public BigDecimal deliveryFee = BigDecimal.ZERO;
    public String externalStatus;
    public Integer estimatedMinutes;
    public LocalDateTime updatedAt;
}
