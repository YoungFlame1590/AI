package com.printshop.mis.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "invoices")
public class InvoiceRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String invoiceNo;
    public Long orderId;
    public String title;
    public String taxNo;
    public BigDecimal amount = BigDecimal.ZERO;
    public String status;
    public String issuedAt;
}
