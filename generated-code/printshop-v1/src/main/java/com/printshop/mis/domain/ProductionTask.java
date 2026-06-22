package com.printshop.mis.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "production_tasks")
public class ProductionTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String taskNo;
    public Long jobTicketId;
    public String station;
    public String operatorName;
    public String plannedStart;
    public String plannedEnd;
    public String status;
    public Integer progressPercent = 0;
    public String qualityStatus;
}
