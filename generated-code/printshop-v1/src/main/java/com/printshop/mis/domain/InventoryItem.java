package com.printshop.mis.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "inventory_items")
public class InventoryItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String sku;
    public String itemName;
    public String category;
    public String unit;
    public BigDecimal quantity = BigDecimal.ZERO;
    public BigDecimal safetyStock = BigDecimal.ZERO;
    public String location;
}
