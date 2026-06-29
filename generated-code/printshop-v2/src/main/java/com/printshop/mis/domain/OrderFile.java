package com.printshop.mis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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
    public String analysisStatus;
    public Integer detectedPageCount;
    public BigDecimal detectedWidthMm;
    public BigDecimal detectedHeightMm;
    public Integer detectedPixelWidth;
    public Integer detectedPixelHeight;
    @Column(name = "detected_dpi_x")
    public BigDecimal detectedDpiX;
    @Column(name = "detected_dpi_y")
    public BigDecimal detectedDpiY;
    public boolean mixedPageSizes;
    public String analysisMessage;
    public LocalDateTime analyzedAt;
    public LocalDateTime uploadedAt;
}
