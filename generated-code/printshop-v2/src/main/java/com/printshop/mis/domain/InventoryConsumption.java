package com.printshop.mis.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_consumptions")
public class InventoryConsumption {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String sku;
    public String itemName;
    public BigDecimal quantity = BigDecimal.ZERO;
    public String storeName;
    public String orderNo;
    public LocalDateTime consumedAt;
}
