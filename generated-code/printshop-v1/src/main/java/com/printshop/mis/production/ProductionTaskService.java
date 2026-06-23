package com.printshop.mis.production;

import static com.printshop.mis.shared.MisSupport.code;
import static com.printshop.mis.shared.MisSupport.notFound;
import static com.printshop.mis.shared.MisSupport.number;
import static com.printshop.mis.shared.MisSupport.text;

import com.printshop.mis.audit.AuditTrailService;
import com.printshop.mis.domain.JobTicket;
import com.printshop.mis.domain.PrintOrder;
import com.printshop.mis.domain.ProductionTask;
import com.printshop.mis.inventory.InventoryService;
import com.printshop.mis.order.OrderStatusPolicy;
import com.printshop.mis.repository.JobTicketRepository;
import com.printshop.mis.repository.PrintOrderRepository;
import com.printshop.mis.repository.ProductionTaskRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProductionTaskService {

    private final ProductionTaskRepository productionTasks;
    private final JobTicketRepository jobTickets;
    private final PrintOrderRepository orders;
    private final InventoryService inventoryService;
    private final OrderStatusPolicy statusPolicy;
    private final AuditTrailService audit;

    public ProductionTaskService(
            ProductionTaskRepository productionTasks,
            JobTicketRepository jobTickets,
            PrintOrderRepository orders,
            InventoryService inventoryService,
            OrderStatusPolicy statusPolicy,
            AuditTrailService audit
    ) {
        this.productionTasks = productionTasks;
        this.jobTickets = jobTickets;
        this.orders = orders;
        this.inventoryService = inventoryService;
        this.statusPolicy = statusPolicy;
        this.audit = audit;
    }

    public ProductionTask createProductionTask(String username, ProductionTask request) {
        JobTicket ticket = jobTickets.findById(request.jobTicketId).orElseThrow(() -> notFound("作业单", request.jobTicketId));
        PrintOrder order = orders.findById(ticket.orderId).orElseThrow(() -> notFound("订单", ticket.orderId));
        statusPolicy.requireStatus(order, java.util.Set.of(OrderStatusPolicy.JOB_READY), "排产任务", "生成作业单");
        ProductionTask task = new ProductionTask();
        task.taskNo = text(request.taskNo, code("PRO"));
        task.jobTicketId = request.jobTicketId;
        task.station = text(request.station, "数码印刷-01");
        task.operatorName = text(request.operatorName, "待分配");
        task.plannedStart = text(request.plannedStart, "今日");
        task.plannedEnd = text(request.plannedEnd, "今日 18:00");
        task.status = "SCHEDULED";
        task.progressPercent = number(request.progressPercent, 0);
        task.qualityStatus = text(request.qualityStatus, "PENDING");
        order.status = OrderStatusPolicy.IN_PRODUCTION;
        order.currentStep = "生产任务已排入工位：" + task.station;
        order.updatedAt = com.printshop.mis.shared.MisSupport.now();
        orders.save(order);
        audit.record(username, "PRO", "CREATE_PRODUCTION_TASK", "PRODUCTION_TASK", task.jobTicketId, task.station);
        return productionTasks.save(task);
    }

    @Transactional(readOnly = true)
    public List<ProductionTask> productionTasks() {
        return productionTasks.findAll();
    }

    @Transactional(readOnly = true)
    public ProductionTask getProductionTask(Long id) {
        return productionTasks.findById(id).orElseThrow(() -> notFound("生产任务", id));
    }

    public ProductionTask updateProductionTask(String username, Long id, ProductionTask request) {
        ProductionTask task = getProductionTask(id);
        task.station = text(request.station, task.station);
        task.operatorName = text(request.operatorName, task.operatorName);
        task.plannedStart = text(request.plannedStart, task.plannedStart);
        task.plannedEnd = text(request.plannedEnd, task.plannedEnd);
        task.status = text(request.status, task.status);
        task.progressPercent = number(request.progressPercent, task.progressPercent);
        task.qualityStatus = text(request.qualityStatus, task.qualityStatus);
        audit.record(username, "PRO", "UPDATE_PRODUCTION_TASK", "PRODUCTION_TASK", id, task.status);
        return productionTasks.save(task);
    }

    public ProductionTask completeProductionTask(String username, Long id) {
        ProductionTask task = getProductionTask(id);
        JobTicket ticket = jobTickets.findById(task.jobTicketId).orElseThrow(() -> notFound("作业单", task.jobTicketId));
        PrintOrder order = orders.findById(ticket.orderId).orElseThrow(() -> notFound("订单", ticket.orderId));
        statusPolicy.requireStatus(order, java.util.Set.of(OrderStatusPolicy.IN_PRODUCTION), "完工质检通过", "完成排产并进入生产");
        task.status = "DONE";
        task.progressPercent = 100;
        task.qualityStatus = "PASS";
        inventoryService.consumeForProduction(username, order.productType, order.colorMode, order.pageCount, order.copies);
        order.status = OrderStatusPolicy.PRODUCTION_DONE;
        order.currentStep = "生产质检完成，等待生成配送或客户自提";
        order.updatedAt = com.printshop.mis.shared.MisSupport.now();
        orders.save(order);
        audit.record(username, "PRO", "COMPLETE_PRODUCTION_TASK", "PRODUCTION_TASK", id, task.taskNo);
        return productionTasks.save(task);
    }

    public ProductionTask deleteProductionTask(String username, Long id) {
        ProductionTask task = getProductionTask(id);
        productionTasks.delete(task);
        audit.record(username, "PRO", "DELETE_PRODUCTION_TASK", "PRODUCTION_TASK", id, task.taskNo);
        return task;
    }
}
