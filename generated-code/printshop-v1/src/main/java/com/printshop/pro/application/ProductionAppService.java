package com.printshop.pro.application;

import com.printshop.common.exception.BusinessException;
import com.printshop.infra.audit.Auditable;
import com.printshop.infra.gateway.DeviceGatewayAdapter;
import com.printshop.infra.state.StateMachine;
import com.printshop.pro.dto.ProductionTask;
import com.printshop.pro.repository.InMemoryProductionTaskRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 排产调度应用服务。
 * 职责：信用额度校验、机台指令下发与异常兜底。
 *
 * @see REQ-PRO-001
 */
@Service
public class ProductionAppService {

    private static final BigDecimal MONTHLY_CREDIT_LIMIT = new BigDecimal("10000.00");

    private final InMemoryProductionTaskRepository productionTaskRepository;
    private final DeviceGatewayAdapter deviceGatewayAdapter;
    private final StateMachine stateMachine;

    public ProductionAppService(
            InMemoryProductionTaskRepository productionTaskRepository,
            DeviceGatewayAdapter deviceGatewayAdapter,
            StateMachine stateMachine
    ) {
        this.productionTaskRepository = productionTaskRepository;
        this.deviceGatewayAdapter = deviceGatewayAdapter;
        this.stateMachine = stateMachine;
    }

    @Auditable(action = "DISPATCH_PRODUCTION")
    public ProductionTask dispatchProduction(ProductionTask request) {
        BigDecimal creditUsed = request.creditLimitUsed() == null ? BigDecimal.ZERO : request.creditLimitUsed();
        if (creditUsed.compareTo(MONTHLY_CREDIT_LIMIT) > 0) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "信用额度超限，需主管审批。");
        }
        String deviceSn = hasText(request.deviceSn()) ? request.deviceSn() : "DEVICE-DEFAULT";
        if (!deviceGatewayAdapter.dispatchToDevice(deviceSn)) {
            throw new BusinessException(HttpStatus.CONFLICT, "机台不可用，排产指令未下发。");
        }
        stateMachine.transit("待排产", "PRODUCTION_DISPATCHED");
        ProductionTask task = new ProductionTask(
                hasText(request.taskId()) ? request.taskId() : "PRO-" + UUID.randomUUID(),
                requiredOrderId(request.orderId()),
                deviceSn,
                hasText(request.slaDeadline()) ? request.slaDeadline() : OffsetDateTime.now().plusHours(4).toString(),
                creditUsed
        );
        return productionTaskRepository.save(task);
    }

    private static String requiredOrderId(String orderId) {
        if (!hasText(orderId)) {
            throw new BusinessException(HttpStatus.CONFLICT, "排产必须绑定订单号。");
        }
        return orderId;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
