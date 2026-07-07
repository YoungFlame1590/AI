package com.printshop.mis.maintenance;

import com.printshop.common.exception.BusinessException;
import com.printshop.infra.stats.StatsRecorder;
import com.printshop.mis.domain.UserAccount;
import com.printshop.mis.identity.IdentityService;
import com.printshop.mis.inventory.InventoryService;
import com.printshop.mis.repository.AuditLogEntryRepository;
import com.printshop.mis.repository.ComplaintTicketRepository;
import com.printshop.mis.repository.CustomerCallbackContactRepository;
import com.printshop.mis.repository.DeliveryQuoteRepository;
import com.printshop.mis.repository.DeliveryTaskRepository;
import com.printshop.mis.repository.DeliveryTrackingEventRepository;
import com.printshop.mis.repository.DesignProjectRepository;
import com.printshop.mis.repository.DesignProjectVersionRepository;
import com.printshop.mis.repository.InventoryItemRepository;
import com.printshop.mis.repository.InventoryConsumptionRepository;
import com.printshop.mis.repository.InvoiceRecordRepository;
import com.printshop.mis.repository.JobTicketRepository;
import com.printshop.mis.repository.OrderFileRepository;
import com.printshop.mis.repository.OrderChangeRequestRepository;
import com.printshop.mis.repository.PaymentRecordRepository;
import com.printshop.mis.repository.PrintOrderRepository;
import com.printshop.mis.repository.ProductionTaskRepository;
import com.printshop.mis.repository.QuotationRepository;
import com.printshop.mis.repository.PurchaseSuggestionRepository;
import com.printshop.mis.repository.ServiceReviewInvitationRepository;
import com.printshop.mis.repository.ServiceReviewRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BusinessDataMaintenanceService {

    private final IdentityService identityService;
    private final PrintOrderRepository orders;
    private final OrderFileRepository orderFiles;
    private final QuotationRepository quotations;
    private final OrderChangeRequestRepository orderChangeRequests;
    private final DesignProjectVersionRepository designProjectVersions;
    private final DesignProjectRepository designProjects;
    private final InventoryConsumptionRepository inventoryConsumptions;
    private final PurchaseSuggestionRepository purchaseSuggestions;
    private final ServiceReviewInvitationRepository serviceReviewInvitations;
    private final ServiceReviewRepository serviceReviews;
    private final ComplaintTicketRepository complaintTickets;
    private final CustomerCallbackContactRepository callbackContacts;
    private final DeliveryQuoteRepository deliveryQuotes;
    private final DeliveryTrackingEventRepository deliveryTrackingEvents;
    private final JobTicketRepository jobTickets;
    private final ProductionTaskRepository productionTasks;
    private final DeliveryTaskRepository deliveryTasks;
    private final InventoryItemRepository inventoryItems;
    private final InvoiceRecordRepository invoices;
    private final PaymentRecordRepository payments;
    private final AuditLogEntryRepository auditLogs;
    private final StatsRecorder stats;
    private final InventoryService inventoryService;

    public BusinessDataMaintenanceService(
            IdentityService identityService,
            PrintOrderRepository orders,
            OrderFileRepository orderFiles,
            OrderChangeRequestRepository orderChangeRequests,
            DesignProjectVersionRepository designProjectVersions,
            DesignProjectRepository designProjects,
            InventoryConsumptionRepository inventoryConsumptions,
            PurchaseSuggestionRepository purchaseSuggestions,
            ServiceReviewInvitationRepository serviceReviewInvitations,
            ServiceReviewRepository serviceReviews,
            ComplaintTicketRepository complaintTickets,
            CustomerCallbackContactRepository callbackContacts,
            DeliveryQuoteRepository deliveryQuotes,
            DeliveryTrackingEventRepository deliveryTrackingEvents,
            QuotationRepository quotations,
            JobTicketRepository jobTickets,
            ProductionTaskRepository productionTasks,
            DeliveryTaskRepository deliveryTasks,
            InventoryItemRepository inventoryItems,
            InvoiceRecordRepository invoices,
            PaymentRecordRepository payments,
            AuditLogEntryRepository auditLogs,
            StatsRecorder stats,
            InventoryService inventoryService
    ) {
        this.identityService = identityService;
        this.orders = orders;
        this.orderFiles = orderFiles;
        this.orderChangeRequests = orderChangeRequests;
        this.designProjectVersions = designProjectVersions;
        this.designProjects = designProjects;
        this.inventoryConsumptions = inventoryConsumptions;
        this.purchaseSuggestions = purchaseSuggestions;
        this.serviceReviewInvitations = serviceReviewInvitations;
        this.serviceReviews = serviceReviews;
        this.complaintTickets = complaintTickets;
        this.callbackContacts = callbackContacts;
        this.deliveryQuotes = deliveryQuotes;
        this.deliveryTrackingEvents = deliveryTrackingEvents;
        this.quotations = quotations;
        this.jobTickets = jobTickets;
        this.productionTasks = productionTasks;
        this.deliveryTasks = deliveryTasks;
        this.inventoryItems = inventoryItems;
        this.invoices = invoices;
        this.payments = payments;
        this.auditLogs = auditLogs;
        this.stats = stats;
        this.inventoryService = inventoryService;
    }

    public Map<String, Object> clearBusinessData(String username) {
        UserAccount user = identityService.requireUser(username);
        if (!"ADMIN".equals(user.role)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只有系统管理员可以清空业务数据。");
        }

        Map<String, Object> deleted = new LinkedHashMap<>();
        deleted.put("auditLogs", auditLogs.count());
        deleted.put("payments", payments.count());
        deleted.put("invoices", invoices.count());
        deleted.put("deliveryTasks", deliveryTasks.count());
        deleted.put("deliveryTrackingEvents", deliveryTrackingEvents.count());
        deleted.put("deliveryQuotes", deliveryQuotes.count());
        deleted.put("complaintTickets", complaintTickets.count());
        deleted.put("callbackContacts", callbackContacts.count());
        deleted.put("serviceReviews", serviceReviews.count());
        deleted.put("serviceReviewInvitations", serviceReviewInvitations.count());
        deleted.put("purchaseSuggestions", purchaseSuggestions.count());
        deleted.put("inventoryConsumptions", inventoryConsumptions.count());
        deleted.put("productionTasks", productionTasks.count());
        deleted.put("jobTickets", jobTickets.count());
        deleted.put("quotations", quotations.count());
        deleted.put("orderChangeRequests", orderChangeRequests.count());
        deleted.put("designProjectVersions", designProjectVersions.count());
        deleted.put("designProjects", designProjects.count());
        deleted.put("orderFiles", orderFiles.count());
        deleted.put("inventoryItems", inventoryItems.count());
        deleted.put("orders", orders.count());

        auditLogs.deleteAllInBatch();
        payments.deleteAllInBatch();
        invoices.deleteAllInBatch();
        deliveryTrackingEvents.deleteAllInBatch();
        deliveryQuotes.deleteAllInBatch();
        deliveryTasks.deleteAllInBatch();
        complaintTickets.deleteAllInBatch();
        callbackContacts.deleteAllInBatch();
        serviceReviews.deleteAllInBatch();
        serviceReviewInvitations.deleteAllInBatch();
        purchaseSuggestions.deleteAllInBatch();
        inventoryConsumptions.deleteAllInBatch();
        productionTasks.deleteAllInBatch();
        jobTickets.deleteAllInBatch();
        quotations.deleteAllInBatch();
        orderChangeRequests.deleteAllInBatch();
        designProjectVersions.deleteAllInBatch();
        designProjects.deleteAllInBatch();
        orderFiles.deleteAllInBatch();
        inventoryItems.deleteAllInBatch();
        orders.deleteAllInBatch();
        inventoryService.ensureDefaultInventory();
        stats.reset();

        return Map.of(
                "message", "业务数据已清空，基础门店、七类演示账号和默认库存已保留。",
                "deleted", deleted
        );
    }
}
