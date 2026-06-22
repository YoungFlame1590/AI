package com.printshop.ord.application;

import com.printshop.common.exception.BusinessException;
import com.printshop.infra.audit.Auditable;
import com.printshop.infra.state.StateMachine;
import com.printshop.ord.dto.Order;
import com.printshop.ord.repository.InMemoryOrderRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 订单接单应用服务。
 * 职责：接单、文件合规校验和状态机初始化，不处理报价逻辑。
 *
 * @see REQ-ORD-001
 */
@Service
public class OrderAppService {

    private static final BigDecimal MAX_FILE_SIZE_MB = new BigDecimal("100.00");
    private static final int MAX_PAGE_COUNT = 80;

    private final InMemoryOrderRepository orderRepository;
    private final StateMachine stateMachine;

    public OrderAppService(InMemoryOrderRepository orderRepository, StateMachine stateMachine) {
        this.orderRepository = orderRepository;
        this.stateMachine = stateMachine;
    }

    @Auditable(action = "CREATE_ORDER")
    public Order createOrder(Order request) {
        BigDecimal fileSize = request.fileSizeMb() == null ? BigDecimal.ZERO : request.fileSizeMb();
        int pageCount = request.pageCount() == null ? 0 : request.pageCount();
        if (fileSize.compareTo(MAX_FILE_SIZE_MB) > 0 || pageCount > MAX_PAGE_COUNT || pageCount <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文件大小或页数超出接单约束。");
        }
        String orderId = hasText(request.orderId()) ? request.orderId() : "ORD-" + UUID.randomUUID();
        String nextStatus = stateMachine.transit(request.orderStatus(), "ORDER_CREATED");
        Order order = new Order(
                orderId,
                nextStatus,
                fileSize,
                pageCount,
                defaultText(request.paymentStatus(), "0未付"),
                defaultText(request.financialVerifyStatus(), "0待核销")
        );
        return orderRepository.save(order);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String defaultText(String value, String defaultValue) {
        return hasText(value) ? value : defaultValue;
    }
}
