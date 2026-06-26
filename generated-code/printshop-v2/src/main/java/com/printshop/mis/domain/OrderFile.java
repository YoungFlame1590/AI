package com.printshop.mis.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_files")
public class OrderFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long orderId;
    public String fileName;
    public String filePath;
    public String contentType;
    public String storageName;
    public Long sizeBytes;
    public String fileStatus;
    public Integer versionNo;
    public String uploadedBy;
    public String uploadedRole;
    public String remark;
    public String reviewStatus;
    public LocalDateTime uploadedAt;
}
