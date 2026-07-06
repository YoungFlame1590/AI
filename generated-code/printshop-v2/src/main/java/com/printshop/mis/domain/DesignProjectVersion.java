package com.printshop.mis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "design_project_versions")
public class DesignProjectVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long projectId;
    public Integer versionNo;
    public String label;
    @Column(length = 5000)
    public String canvasJson;
    public String savedBy;
    public LocalDateTime createdAt;
}
