package com.printshop.mis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "purchase_suggestions")
public class PurchaseSuggestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String suggestionNo;
    public String sku;
    public String itemName;
    public BigDecimal currentQuantity = BigDecimal.ZERO;
    public BigDecimal dynamicSafetyStock = BigDecimal.ZERO;
    public BigDecimal recommendedQuantity = BigDecimal.ZERO;
    public BigDecimal estimatedCost = BigDecimal.ZERO;
    public String supplierName;
    @Column(length = 1000)
    public String reason;
    public String status;
    public LocalDateTime createdAt;
    public String approvedBy;
    public LocalDateTime approvedAt;
}
