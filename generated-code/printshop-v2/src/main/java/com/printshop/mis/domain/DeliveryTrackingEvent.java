package com.printshop.mis.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "delivery_tracking_events")
public class DeliveryTrackingEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long deliveryTaskId;
    public String trackingNo;
    public String status;
    public String location;
    public String message;
    public LocalDateTime occurredAt;
}
