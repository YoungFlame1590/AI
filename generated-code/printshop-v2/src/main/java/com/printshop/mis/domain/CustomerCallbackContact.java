package com.printshop.mis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_callback_contacts")
public class CustomerCallbackContact {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long customerId;
    public String customerName;
    public Long storeId;
    public String contactedBy;
    public LocalDateTime contactedAt;
    @Column(length = 1000)
    public String note;
}
