package com.printshop.mis.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "print_orders")
public class PrintOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String orderNo;
    public Long customerId;
    public String customerName;
    public Long storeId;
    public String storeName;
    public String productType;
    public String colorMode;
    public Integer pageCount;
    public Integer copies;
    public String sizeName;
    public String paperType;
    public String craftType;
    public String dueAt;
    public String deliveryMode;
    public String priority;
    public String status;
    public String paymentStatus;
    public String currentStep;
    public BigDecimal totalAmount = BigDecimal.ZERO;
    public BigDecimal paidAmount = BigDecimal.ZERO;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
