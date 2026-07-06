package com.printshop.mis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "design_templates")
public class DesignTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String templateNo;
    public String title;
    public String category;
    public String productType;
    public String colorMode;
    public Integer pageCount;
    public Integer defaultCopies;
    public String sizeName;
    public String priceType;
    public Boolean published = true;
    @Column(length = 5000)
    public String canvasJson;
    public String createdBy;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
