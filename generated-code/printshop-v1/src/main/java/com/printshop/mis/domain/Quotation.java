package com.printshop.mis.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "quotations")
public class Quotation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String quoteNo;
    public Long orderId;
    public Integer versionNo = 1;
    public BigDecimal subtotal = BigDecimal.ZERO;
    public BigDecimal discountRate = BigDecimal.ONE;
    public BigDecimal finalAmount = BigDecimal.ZERO;
    public String status;
    public String approvedBy;
    public String validUntil;
    public LocalDateTime createdAt;
}
