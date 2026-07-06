package com.printshop.mis.order;

import com.printshop.mis.domain.AuditLogEntry;
import com.printshop.mis.domain.JobTicket;
import com.printshop.mis.domain.PrintOrder;
import com.printshop.mis.domain.UserAccount;
import com.printshop.mis.identity.IdentityService;
import com.printshop.mis.repository.AuditLogEntryRepository;
import com.printshop.mis.repository.ComplaintTicketRepository;
import com.printshop.mis.repository.DeliveryQuoteRepository;
import com.printshop.mis.repository.DeliveryTaskRepository;
import com.printshop.mis.repository.DeliveryTrackingEventRepository;
import com.printshop.mis.repository.InvoiceRecordRepository;
import com.printshop.mis.repository.JobTicketRepository;
import com.printshop.mis.repository.OrderChangeRequestRepository;
import com.printshop.mis.repository.OrderFileRepository;
import com.printshop.mis.repository.PaymentRecordRepository;
import com.printshop.mis.repository.ProductionTaskRepository;
import com.printshop.mis.repository.QuotationRepository;
import com.printshop.mis.repository.ServiceReviewRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderAggregateService {

    private final IdentityService identityService;
    private final OrderService orderService;
    private final OrderWorkflowService workflowService;
    private final QuotationRepository quotations;
    private final JobTicketRepository jobTickets;
    private final ProductionTaskRepository productionTasks;
    private final DeliveryTaskRepository deliveryTasks;
    private final DeliveryQuoteRepository deliveryQuotes;
    private final DeliveryTrackingEventRepository trackingEvents;
    private final PaymentRecordRepository payments;
    private final InvoiceRecordRepository invoices;
    private final ServiceReviewRepository serviceReviews;
    private final ComplaintTicketRepository complaintTickets;
    private final OrderChangeRequestRepository changeRequests;
    private final OrderFileRepository files;
    private final AuditLogEntryRepository audits;

    public OrderAggregateService(
            IdentityService identityService,
            OrderService orderService,
            OrderWorkflowService workflowService,
            QuotationRepository quotations,
            JobTicketRepository jobTickets,
            ProductionTaskRepository productionTasks,
            DeliveryTaskRepository deliveryTasks,
            DeliveryQuoteRepository deliveryQuotes,
            DeliveryTrackingEventRepository trackingEvents,
            PaymentRecordRepository payments,
            InvoiceRecordRepository invoices,
            ServiceReviewRepository serviceReviews,
            ComplaintTicketRepository complaintTickets,
            OrderChangeRequestRepository changeRequests,
            OrderFileRepository files,
            AuditLogEntryRepository audits
    ) {
        this.identityService = identityService;
        this.orderService = orderService;
        this.workflowService = workflowService;
        this.quotations = quotations;
        this.jobTickets = jobTickets;
        this.productionTasks = productionTasks;
        this.deliveryTasks = deliveryTasks;
        this.deliveryQuotes = deliveryQuotes;
        this.trackingEvents = trackingEvents;
        this.payments = payments;
        this.invoices = invoices;
        this.serviceReviews = serviceReviews;
        this.complaintTickets = complaintTickets;
        this.changeRequests = changeRequests;
        this.files = files;
        this.audits = audits;
    }

    public Map<String, Object> aggregate(String username, Long orderId) {
        UserAccount user = identityService.requireUser(username);
        PrintOrder order = orderService.requireVisibleOrder(username, orderId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("order", order);
        result.put("quotations", quotations.findByOrderIdOrderByCreatedAtDesc(order.id));
        result.put("jobTickets", jobTickets.findByOrderIdOrderByCreatedAtDesc(order.id));
        result.put("productionTasks", jobTickets.findByOrderIdOrderByCreatedAtDesc(order.id).stream()
                .flatMap(ticket -> productionTasks.findByJobTicketId(ticket.id).stream())
                .toList());
        var orderDeliveryTasks = deliveryTasks.findByOrderId(order.id);
        result.put("deliveryTasks", orderDeliveryTasks);
        result.put("deliveryQuotes", deliveryQuotes.findByOrderIdOrderByCreatedAtDesc(order.id));
        result.put("deliveryTracking", orderDeliveryTasks.stream()
                .collect(java.util.stream.Collectors.toMap(
                        task -> task.id,
                        task -> trackingEvents.findByDeliveryTaskIdOrderByOccurredAtDesc(task.id),
                        (left, right) -> left,
                        LinkedHashMap::new
                )));
        result.put("payments", payments.findByOrderId(order.id));
        result.put("invoices", invoices.findByOrderId(order.id));
        result.put("serviceReviews", serviceReviews.findAll().stream()
                .filter(review -> order.id.equals(review.orderId))
                .toList());
        result.put("complaintTickets", complaintTickets.findAllByOrderByCreatedAtDesc().stream()
                .filter(ticket -> order.id.equals(ticket.orderId))
                .toList());
        result.put("changeRequests", changeRequests.findByOrderIdOrderByCreatedAtDesc(order.id));
        result.put("files", files.findByOrderIdOrderByUploadedAtDesc(order.id));
        result.put("nextTasks", workflowService.nextActions(username, order.id));
        if (!"CUSTOMER".equals(user.role) && !"COURIER".equals(user.role)) {
            result.put("audits", audits.findTop20ByOrderByCreatedAtDesc().stream()
                    .filter(entry -> belongsToOrder(entry, order))
                    .toList());
        }
        return result;
    }

    private boolean belongsToOrder(AuditLogEntry entry, PrintOrder order) {
        if ("ORDER".equals(entry.targetType) && String.valueOf(order.id).equals(String.valueOf(entry.targetId))) {
            return true;
        }
        String detail = entry.detail == null ? "" : entry.detail;
        return detail.contains(order.orderNo);
    }
}
