package com.printshop.mis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "design_projects")
public class DesignProject {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String projectNo;
    public Long templateId;
    public Long customerId;
    public String customerName;
    public Long storeId;
    public String title;
    public String status;
    public Integer currentVersionNo = 1;
    @Column(length = 5000)
    public String canvasJson;
    public Long submittedOrderId;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
