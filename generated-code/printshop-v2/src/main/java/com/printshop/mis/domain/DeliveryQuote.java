package com.printshop.mis.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "delivery_quotes")
public class DeliveryQuote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String quoteNo;
    public Long orderId;
    public String channelCode;
    public String channelName;
    public String pickupAddress;
    public String deliveryAddress;
    public BigDecimal packageWeightKg = BigDecimal.ONE;
    public BigDecimal estimatedFee = BigDecimal.ZERO;
    public Integer estimatedMinutes = 60;
    public String status;
    public LocalDateTime createdAt;
    public LocalDateTime confirmedAt;
}
