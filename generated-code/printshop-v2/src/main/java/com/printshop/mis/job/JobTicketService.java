package com.printshop.mis.job;

import static com.printshop.mis.shared.MisSupport.code;
import static com.printshop.mis.shared.MisSupport.notFound;
import static com.printshop.mis.shared.MisSupport.now;
import static com.printshop.mis.shared.MisSupport.text;

import com.printshop.mis.audit.AuditTrailService;
import com.printshop.mis.domain.JobTicket;
import com.printshop.mis.domain.PrintOrder;
import com.printshop.mis.identity.IdentityService;
import com.printshop.mis.order.OrderService;
import com.printshop.mis.order.OrderStatusPolicy;
import com.printshop.mis.repository.JobTicketRepository;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class JobTicketService {

    private final OrderService orderService;
    private final IdentityService identityService;
    private final OrderStatusPolicy statusPolicy;
    private final JobTicketRepository jobTickets;
    private final AuditTrailService audit;

    public JobTicketService(OrderService orderService, IdentityService identityService, OrderStatusPolicy statusPolicy, JobTicketRepository jobTickets, AuditTrailService audit) {
        this.orderService = orderService;
        this.identityService = identityService;
        this.statusPolicy = statusPolicy;
        this.jobTickets = jobTickets;
        this.audit = audit;
    }

    public JobTicket createJobTicket(String username, JobTicket request) {
        PrintOrder order = orderService.requireVisibleOrder(username, request.orderId);
        statusPolicy.requireStatus(order, java.util.Set.of(OrderStatusPolicy.QUOTED), "生成作业单", "生成报价");
        JobTicket ticket = new JobTicket();
        ticket.ticketNo = text(request.ticketNo, code("JOB"));
        ticket.orderId = order.id;
        ticket.quotationId = request.quotationId;
        ticket.specs = text(request.specs, order.productType + " / " + order.colorMode + " / " + text(order.sizeName, "未指定尺寸") + " / " + text(order.craftType, "无特殊工艺"));
        ticket.paperType = text(request.paperType, text(order.paperType, "A4 80g"));
        ticket.binding = text(request.binding, text(order.craftType, "普通装订"));
        ticket.status = "READY";
        ticket.createdAt = now();
        order.status = "JOB_READY";
        order.currentStep = "作业单已生成，等待排产";
        order.updatedAt = now();
        orderService.saveOrder(order);
        audit.record(username, "PRO", "CREATE_JOB_TICKET", "JOB_TICKET", order.id, ticket.ticketNo);
        return jobTickets.save(ticket);
    }

    @Transactional(readOnly = true)
    public List<JobTicket> jobTickets(String username) {
        Set<Long> visibleOrderIds = visibleOrderIds(username);
        return jobTickets.findAll().stream()
                .filter(ticket -> visibleOrderIds.contains(ticket.orderId))
                .toList();
    }

    @Transactional(readOnly = true)
    public JobTicket getJobTicket(String username, Long id) {
        JobTicket ticket = jobTickets.findById(id).orElseThrow(() -> notFound("作业单", id));
        orderService.requireVisibleOrder(username, ticket.orderId);
        return ticket;
    }

    public JobTicket updateJobTicket(String username, Long id, JobTicket request) {
        JobTicket ticket = getJobTicket(username, id);
        ticket.specs = text(request.specs, ticket.specs);
        ticket.paperType = text(request.paperType, ticket.paperType);
        ticket.binding = text(request.binding, ticket.binding);
        ticket.status = text(request.status, ticket.status);
        audit.record(username, "PRO", "UPDATE_JOB_TICKET", "JOB_TICKET", id, ticket.status);
        return jobTickets.save(ticket);
    }

    public JobTicket deleteJobTicket(String username, Long id) {
        JobTicket ticket = getJobTicket(username, id);
        jobTickets.delete(ticket);
        audit.record(username, "PRO", "DELETE_JOB_TICKET", "JOB_TICKET", id, ticket.ticketNo);
        return ticket;
    }

    private Set<Long> visibleOrderIds(String username) {
        return orderService.visibleOrders(identityService.requireUser(username)).stream()
                .map(order -> order.id)
                .collect(java.util.stream.Collectors.toSet());
    }
}
