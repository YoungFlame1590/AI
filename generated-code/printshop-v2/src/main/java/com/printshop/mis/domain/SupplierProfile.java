package com.printshop.mis.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "supplier_profiles")
public class SupplierProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String sku;
    public String supplierName;
    public Integer leadTimeDays = 3;
    public BigDecimal minOrderQuantity = BigDecimal.ONE;
    public BigDecimal unitPrice = BigDecimal.ONE;
    public String discountBreaks;
    public Boolean active = true;
}
