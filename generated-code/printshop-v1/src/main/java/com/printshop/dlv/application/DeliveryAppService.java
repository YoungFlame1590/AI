package com.printshop.dlv.application;

import com.printshop.common.exception.BusinessException;
import com.printshop.dlv.dto.DeliveryTask;
import com.printshop.dlv.repository.InMemoryDeliveryTaskRepository;
import com.printshop.infra.audit.Auditable;
import com.printshop.infra.state.StateMachine;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 配送外协应用服务。
 * 职责：跨店路由决策、财务核销状态同步和任务下发。
 *
 * @see REQ-DLV-001
 */
@Service
public class DeliveryAppService {

    private static final BigDecimal MAX_OUTSOURCING_RATIO = new BigDecimal("100.00");

    private final InMemoryDeliveryTaskRepository deliveryTaskRepository;
    private final StateMachine stateMachine;

    public DeliveryAppService(InMemoryDeliveryTaskRepository deliveryTaskRepository, StateMachine stateMachine) {
        this.deliveryTaskRepository = deliveryTaskRepository;
        this.stateMachine = stateMachine;
    }

    @Auditable(action = "ROUTE_DELIVERY")
    public DeliveryTask routeDelivery(DeliveryTask request) {
        BigDecimal ratio = request.outsourcingCostRatio() == null ? BigDecimal.ZERO : request.outsourcingCostRatio();
        if (ratio.compareTo(MAX_OUTSOURCING_RATIO) > 0) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "外协成本占比输入超出物理范围。");
        }
        stateMachine.transit("生产中", "DELIVERY_ROUTED");
        DeliveryTask task = new DeliveryTask(
                hasText(request.taskId()) ? request.taskId() : "DLV-" + UUID.randomUUID(),
                requiredOrderId(request.orderId()),
                hasText(request.targetStoreId()) ? request.targetStoreId() : "STORE-A",
                hasText(request.financialVerifyStatus()) ? request.financialVerifyStatus() : "0待核销",
                ratio
        );
        return deliveryTaskRepository.save(task);
    }

    private static String requiredOrderId(String orderId) {
        if (!hasText(orderId)) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "配送路由必须绑定订单号。");
        }
        return orderId;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
