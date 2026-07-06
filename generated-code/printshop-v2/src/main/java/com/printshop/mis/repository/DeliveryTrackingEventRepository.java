package com.printshop.mis.repository;

import com.printshop.mis.domain.DeliveryTrackingEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryTrackingEventRepository extends JpaRepository<DeliveryTrackingEvent, Long> {
    List<DeliveryTrackingEvent> findByDeliveryTaskIdOrderByOccurredAtDesc(Long deliveryTaskId);
}
