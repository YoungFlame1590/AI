package com.printshop.mis.feedback;

import static com.printshop.mis.shared.MisSupport.asString;
import static com.printshop.mis.shared.MisSupport.code;
import static com.printshop.mis.shared.MisSupport.forbidden;
import static com.printshop.mis.shared.MisSupport.notFound;
import static com.printshop.mis.shared.MisSupport.now;
import static com.printshop.mis.shared.MisSupport.text;

import com.printshop.common.exception.BusinessException;
import com.printshop.mis.audit.AuditTrailService;
import com.printshop.mis.domain.ComplaintTicket;
import com.printshop.mis.domain.CustomerCallbackContact;
import com.printshop.mis.domain.PrintOrder;
import com.printshop.mis.domain.ServiceReview;
import com.printshop.mis.domain.ServiceReviewInvitation;
import com.printshop.mis.domain.UserAccount;
import com.printshop.mis.identity.IdentityService;
import com.printshop.mis.order.OrderService;
import com.printshop.mis.repository.ComplaintTicketRepository;
import com.printshop.mis.repository.CustomerCallbackContactRepository;
import com.printshop.mis.repository.PrintOrderRepository;
import com.printshop.mis.repository.ServiceReviewInvitationRepository;
import com.printshop.mis.repository.ServiceReviewRepository;
import com.printshop.mis.repository.UserAccountRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FeedbackService {

    private final IdentityService identityService;
    private final OrderService orderService;
    private final PrintOrderRepository orders;
    private final UserAccountRepository users;
    private final ServiceReviewInvitationRepository invitations;
    private final ServiceReviewRepository reviews;
    private final ComplaintTicketRepository complaints;
    private final CustomerCallbackContactRepository callbackContacts;
    private final AuditTrailService audit;

    public FeedbackService(
            IdentityService identityService,
            OrderService orderService,
            PrintOrderRepository orders,
            UserAccountRepository users,
            ServiceReviewInvitationRepository invitations,
            ServiceReviewRepository reviews,
            ComplaintTicketRepository complaints,
            CustomerCallbackContactRepository callbackContacts,
            AuditTrailService audit
    ) {
        this.identityService = identityService;
        this.orderService = orderService;
        this.orders = orders;
        this.users = users;
        this.invitations = invitations;
        this.reviews = reviews;
        this.complaints = complaints;
        this.callbackContacts = callbackContacts;
        this.audit = audit;
    }

    public ServiceReviewInvitation ensureInvitation(String username, PrintOrder order) {
        return invitations.findByOrderId(order.id).orElseGet(() -> {
            ServiceReviewInvitation invitation = new ServiceReviewInvitation();
            invitation.orderId = order.id;
            invitation.orderNo = order.orderNo;
            invitation.customerId = order.customerId;
            invitation.customerName = order.customerName;
            invitation.storeId = order.storeId;
            invitation.status = "PENDING";
            invitation.invitedAt = now();
            ServiceReviewInvitation saved = invitations.save(invitation);
            audit.record(username, "FBK", "CREATE_REVIEW_INVITATION", "ORDER", order.id, order.orderNo);
            return saved;
        });
    }

    @Transactional(readOnly = true)
    public List<ServiceReviewInvitation> invitations(String username) {
        UserAccount user = identityService.requireUser(username);
        return switch (user.role) {
            case "CUSTOMER" -> invitations.findByCustomerIdOrderByInvitedAtDesc(user.id);
            case "MANAGER", "CLERK" -> invitations.findAll().stream()
                    .filter(item -> user.storeId != null && user.storeId.equals(item.storeId))
                    .sorted(Comparator.comparing((ServiceReviewInvitation item) -> item.invitedAt).reversed())
                    .toList();
            case "OPS", "ADMIN" -> invitations.findAll().stream()
                    .sorted(Comparator.comparing((ServiceReviewInvitation item) -> item.invitedAt).reversed())
                    .toList();
            default -> List.of();
        };
    }

    public ServiceReview submitReview(String username, Long orderId, Map<String, Object> payload) {
        UserAccount user = identityService.requireUser(username);
        PrintOrder order = orderService.requireVisibleOrder(username, orderId);
        if (!"CUSTOMER".equals(user.role) && !"ADMIN".equals(user.role)) {
            throw forbidden("只有客户或管理员可以提交服务评价。");
        }
        if (!"DONE".equals(order.status)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "订单完成后才能评价。");
        }
        if (reviews.existsByOrderId(order.id)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该订单已评价。");
        }
        ServiceReview review = new ServiceReview();
        review.orderId = order.id;
        review.orderNo = order.orderNo;
        review.customerId = order.customerId;
        review.customerName = order.customerName;
        review.storeId = order.storeId;
        review.printQualityRating = rating(payload.get("printQualityRating"));
        review.timelinessRating = rating(payload.get("timelinessRating"));
        review.staffRating = rating(payload.get("staffRating"));
        review.valueRating = rating(payload.get("valueRating"));
        review.overallRating = Math.round((review.printQualityRating + review.timelinessRating + review.staffRating + review.valueRating) / 4.0f);
        review.comment = text(asString(payload.get("comment")), "");
        review.createdAt = now();
        ServiceReview saved = reviews.save(review);
        invitations.findByOrderId(order.id).ifPresent(invitation -> {
            invitation.status = "RESPONDED";
            invitation.respondedAt = now();
            invitations.save(invitation);
        });
        if (saved.overallRating <= 2) {
            createComplaint(saved);
        }
        audit.record(username, "FBK", "SUBMIT_SERVICE_REVIEW", "ORDER", order.id, "rating=" + saved.overallRating);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ServiceReview> serviceReviews(String username) {
        UserAccount user = identityService.requireUser(username);
        return switch (user.role) {
            case "CUSTOMER" -> reviews.findByCustomerIdOrderByCreatedAtDesc(user.id);
            case "MANAGER", "CLERK" -> reviews.findAll().stream()
                    .filter(item -> user.storeId != null && user.storeId.equals(item.storeId))
                    .toList();
            case "OPS", "ADMIN" -> reviews.findAll();
            default -> List.of();
        };
    }

    @Transactional(readOnly = true)
    public List<ComplaintTicket> complaintTickets(String username) {
        UserAccount user = identityService.requireUser(username);
        return switch (user.role) {
            case "MANAGER", "CLERK" -> complaints.findByStoreIdOrderByCreatedAtDesc(user.storeId);
            case "OPS", "ADMIN" -> complaints.findAllByOrderByCreatedAtDesc();
            default -> List.of();
        };
    }

    public ComplaintTicket replyComplaint(String username, Long id, Map<String, Object> payload) {
        UserAccount user = requireManager(username);
        ComplaintTicket ticket = complaints.findById(id).orElseThrow(() -> notFound("客诉工单", id));
        requireTicketStore(user, ticket);
        ticket.status = "REPLIED";
        ticket.managerReply = text(asString(payload.get("reply")), "已联系客户处理。");
        ticket.repliedBy = user.displayName;
        ticket.repliedAt = now();
        audit.record(username, "FBK", "REPLY_COMPLAINT", "COMPLAINT", id, ticket.orderNo);
        return complaints.save(ticket);
    }

    public ComplaintTicket closeComplaint(String username, Long id) {
        UserAccount user = requireManager(username);
        ComplaintTicket ticket = complaints.findById(id).orElseThrow(() -> notFound("客诉工单", id));
        requireTicketStore(user, ticket);
        ticket.status = "CLOSED";
        ticket.closedAt = now();
        audit.record(username, "FBK", "CLOSE_COMPLAINT", "COMPLAINT", id, ticket.orderNo);
        return complaints.save(ticket);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> callbackReminders(String username) {
        UserAccount user = identityService.requireUser(username);
        if (!List.of("MANAGER", "CLERK", "OPS", "ADMIN").contains(user.role)) {
            throw forbidden("当前角色不能查看客户回访提醒。");
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        return users.findAll().stream()
                .filter(account -> "CUSTOMER".equals(account.role))
                .filter(account -> user.storeId == null || account.storeId == null || user.storeId.equals(account.storeId) || List.of("OPS", "ADMIN").contains(user.role))
                .map(account -> callbackFor(account, cutoff))
                .filter(item -> Boolean.TRUE.equals(item.get("due")))
                .filter(item -> !callbackContacts.existsByCustomerIdAndStoreIdAndContactedAtAfter(
                        Long.valueOf(String.valueOf(item.get("customerId"))),
                        item.get("storeId") == null ? null : Long.valueOf(String.valueOf(item.get("storeId"))),
                        cutoff))
                .toList();
    }

    public Map<String, Object> markCallbackContacted(String username, Long customerId, Map<String, Object> payload) {
        UserAccount user = requireCallbackManager(username);
        UserAccount customer = users.findById(customerId).orElseThrow(() -> notFound("客户", customerId));
        if (!"CUSTOMER".equals(customer.role)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "只能标记客户回访。");
        }
        if (!"ADMIN".equals(user.role) && (user.storeId == null || customer.storeId == null || !user.storeId.equals(customer.storeId))) {
            throw notFound("客户", customerId);
        }
        CustomerCallbackContact contact = new CustomerCallbackContact();
        contact.customerId = customer.id;
        contact.customerName = customer.displayName;
        contact.storeId = customer.storeId;
        contact.contactedBy = user.displayName;
        contact.contactedAt = now();
        contact.note = text(asString(payload.get("note")), "已完成电话/微信回访。");
        CustomerCallbackContact saved = callbackContacts.save(contact);
        audit.record(username, "FBK", "MARK_CALLBACK_CONTACTED", "CUSTOMER", customer.id, customer.displayName);
        return Map.of(
                "message", "已标记客户回访，30天内不再提醒。",
                "contact", saved
        );
    }

    private void createComplaint(ServiceReview review) {
        ComplaintTicket ticket = new ComplaintTicket();
        ticket.ticketNo = code("CPT");
        ticket.reviewId = review.id;
        ticket.orderId = review.orderId;
        ticket.orderNo = review.orderNo;
        ticket.customerName = review.customerName;
        ticket.storeId = review.storeId;
        ticket.status = "OPEN";
        ticket.severity = review.overallRating <= 1 ? "HIGH" : "MEDIUM";
        ticket.customerComment = review.comment;
        ticket.createdAt = now();
        complaints.save(ticket);
    }

    private Map<String, Object> callbackFor(UserAccount account, LocalDateTime cutoff) {
        List<PrintOrder> customerOrders = orders.findAll().stream()
                .filter(order -> account.id.equals(order.customerId))
                .toList();
        LocalDateTime lastOrderAt = customerOrders.stream()
                .map(order -> order.createdAt)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        boolean due = lastOrderAt == null || lastOrderAt.isBefore(cutoff);
        Map<String, Object> reminder = new LinkedHashMap<>();
        reminder.put("id", account.id);
        reminder.put("customerId", account.id);
        reminder.put("customerName", account.displayName);
        reminder.put("storeId", account.storeId);
        reminder.put("lastOrderAt", lastOrderAt);
        reminder.put("due", due);
        reminder.put("reason", lastOrderAt == null ? "客户暂无订单记录，建议首次关怀" : "客户超过30天未消费，建议回访");
        return reminder;
    }

    private int rating(Object value) {
        int rating = value == null ? 5 : Integer.parseInt(String.valueOf(value));
        if (rating < 1 || rating > 5) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "评分必须在 1-5 星之间。");
        }
        return rating;
    }

    private UserAccount requireManager(String username) {
        UserAccount user = identityService.requireUser(username);
        if (!List.of("MANAGER", "ADMIN").contains(user.role)) {
            throw forbidden("只有门店店长或系统管理员可以处理客诉。");
        }
        return user;
    }

    private UserAccount requireCallbackManager(String username) {
        UserAccount user = identityService.requireUser(username);
        if (!List.of("MANAGER", "ADMIN").contains(user.role)) {
            throw forbidden("只有门店店长或系统管理员可以标记客户回访。");
        }
        return user;
    }

    private void requireTicketStore(UserAccount user, ComplaintTicket ticket) {
        if ("ADMIN".equals(user.role)) {
            return;
        }
        if (user.storeId == null || !user.storeId.equals(ticket.storeId)) {
            throw notFound("客诉工单", ticket.id);
        }
    }
}
