package com.printshop.mis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "service_reviews")
public class ServiceReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long orderId;
    public String orderNo;
    public Long customerId;
    public String customerName;
    public Long storeId;
    public Integer printQualityRating;
    public Integer timelinessRating;
    public Integer staffRating;
    public Integer valueRating;
    public Integer overallRating;
    @Column(length = 1000)
    public String comment;
    public LocalDateTime createdAt;
}
