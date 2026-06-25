package com.printshop.mis.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_change_requests")
public class OrderChangeRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String requestNo;
    public Long orderId;
    public String orderNo;
    public Long requestedById;
    public String requestedBy;
    public String requesterRole;
    public String status;
    public String reason;
    public String oldProductType;
    public String newProductType;
    public String oldColorMode;
    public String newColorMode;
    public Integer oldPageCount;
    public Integer newPageCount;
    public Integer oldCopies;
    public Integer newCopies;
    public String oldDeliveryMode;
    public String newDeliveryMode;
    public String oldPriority;
    public String newPriority;
    public BigDecimal oldAmount = BigDecimal.ZERO;
    public BigDecimal newAmount = BigDecimal.ZERO;
    public BigDecimal amountDelta = BigDecimal.ZERO;
    public String approvedBy;
    public String decisionComment;
    public LocalDateTime freezeStartedAt;
    public LocalDateTime freezeEndedAt;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
