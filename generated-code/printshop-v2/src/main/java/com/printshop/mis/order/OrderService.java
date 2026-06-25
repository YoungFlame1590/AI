package com.printshop.mis.order;

import static com.printshop.mis.shared.MisSupport.asString;
import static com.printshop.mis.shared.MisSupport.code;
import static com.printshop.mis.shared.MisSupport.forbidden;
import static com.printshop.mis.shared.MisSupport.now;
import static com.printshop.mis.shared.MisSupport.number;
import static com.printshop.mis.shared.MisSupport.text;

import com.printshop.common.exception.BusinessException;
import com.printshop.mis.audit.AuditTrailService;
import com.printshop.mis.domain.OrderFile;
import com.printshop.mis.domain.PrintOrder;
import com.printshop.mis.domain.UserAccount;
import com.printshop.mis.identity.IdentityService;
import com.printshop.mis.repository.OrderFileRepository;
import com.printshop.mis.repository.PrintOrderRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class OrderService {

    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final IdentityService identityService;
    private final PrintOrderRepository orders;
    private final OrderFileRepository files;
    private final AuditTrailService audit;
    private final OrderPricingPolicy pricing;
    private final OrderAccessPolicy access;
    private final OrderStatusPolicy statusPolicy;
    private final Path uploadRoot;

    public OrderService(
            IdentityService identityService,
            PrintOrderRepository orders,
            OrderFileRepository files,
            AuditTrailService audit,
            OrderPricingPolicy pricing,
            OrderAccessPolicy access,
            OrderStatusPolicy statusPolicy,
            @Value("${printshop.upload-dir:uploads}") String uploadDir
    ) {
        this.identityService = identityService;
        this.orders = orders;
        this.files = files;
        this.audit = audit;
        this.pricing = pricing;
        this.access = access;
        this.statusPolicy = statusPolicy;
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public PrintOrder createOrder(String username, PrintOrder request) {
        UserAccount user = identityService.requireUser(username);
        LocalDateTime timestamp = now();
        PrintOrder order = new PrintOrder();
        order.orderNo = text(request.orderNo, code("ORD"));
        order.customerId = user.id;
        order.customerName = user.displayName;
        order.storeId = user.storeId;
        order.storeName = identityService.storeName(order.storeId);
        order.productType = pricing.normalizeOption(text(request.productType, "论文胶装"), OrderPricingPolicy.PRODUCT_TYPES, "产品类型");
        order.colorMode = pricing.normalizeOption(text(request.colorMode, "黑白"), OrderPricingPolicy.COLOR_MODES, "颜色/工艺");
        order.pageCount = number(request.pageCount, 1);
        order.copies = number(request.copies, 1);
        pricing.validatePositive(order.pageCount, "页数");
        pricing.validatePositive(order.copies, "份数");
        order.dueAt = text(request.dueAt, DISPLAY_TIME.format(timestamp));
        order.deliveryMode = pricing.normalizeOption(text(request.deliveryMode, "到店自提"), OrderPricingPolicy.DELIVERY_MODES, "交付方式");
        order.priority = pricing.normalizeOption(text(request.priority, "普通"), OrderPricingPolicy.PRIORITIES, "优先级");
        order.status = "SUBMITTED";
        order.paymentStatus = "UNPAID";
        order.currentStep = "客户已提交订单，等待门店审核文件";
        order.totalAmount = pricing.calculate(order);
        order.paidAmount = BigDecimal.ZERO;
        order.createdAt = timestamp;
        order.updatedAt = timestamp;
        PrintOrder saved = orders.save(order);
        audit.record(username, "ORD", "CREATE_ORDER", "ORDER", saved.id, saved.orderNo);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<PrintOrder> listOrders(String username) {
        return visibleOrders(identityService.requireUser(username));
    }

    @Transactional(readOnly = true)
    public List<PrintOrder> visibleOrders(UserAccount user) {
        return orders.findAll().stream()
                .filter(order -> access.canViewOrder(user, order))
                .sorted(Comparator.comparing((PrintOrder order) -> order.updatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public PrintOrder getOrder(String username, Long id) {
        return access.requireVisibleOrder(identityService.requireUser(username), id);
    }

    public PrintOrder updateOrder(String username, Long id, PrintOrder request) {
        UserAccount user = identityService.requireUser(username);
        PrintOrder order = access.requireVisibleOrder(user, id);
        boolean hasPricingChange = hasAnyOrderField(request, "productType", "colorMode", "pageCount", "copies", "deliveryMode", "priority");
        if (hasPricingChange && !OrderAccessPolicy.EARLY_ORDER_STATUSES.contains(order.status)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "订单已进入处理流程，规格、数量、交付或加急变更请通过订单变更请求提交店长审批。");
        }
        applyOrderUpdate(user, order, request);
        order.updatedAt = now();
        audit.record(username, "ORD", "UPDATE_ORDER", "ORDER", order.id, order.orderNo);
        return orders.save(order);
    }

    public PrintOrder changeOrderStatus(String username, Long id, Map<String, Object> payload) {
        UserAccount user = identityService.requireUser(username);
        PrintOrder order = access.requireVisibleOrder(user, id);
        String requestedStatus = text(asString(payload.get("status")), order.status);
        if ("CUSTOMER".equals(user.role)) {
            if (!OrderStatusPolicy.REVIEWING.equals(requestedStatus) || !OrderAccessPolicy.EARLY_ORDER_STATUSES.contains(order.status)) {
                throw forbidden("客户只能将早期订单提交审核。");
            }
        } else {
            access.assertCanChangeOrderStatus(user);
            statusPolicy.assertManualTransition(order, requestedStatus);
        }
        order.status = requestedStatus;
        order.currentStep = text(asString(payload.get("step")), "订单状态已流转");
        order.updatedAt = now();
        audit.record(username, "ORD", "CHANGE_ORDER_STATUS", "ORDER", order.id, order.status);
        return orders.save(order);
    }

    public PrintOrder deleteOrder(String username, Long id) {
        UserAccount user = identityService.requireUser(username);
        PrintOrder order = access.requireVisibleOrder(user, id);
        access.assertCanDeleteOrder(user, order);
        orders.delete(order);
        audit.record(username, "ORD", "DELETE_ORDER", "ORDER", id, order.orderNo);
        return order;
    }

    public OrderFile uploadFile(String username, Long orderId, MultipartFile upload) {
        PrintOrder order = access.requireVisibleOrder(identityService.requireUser(username), orderId);
        try {
            Files.createDirectories(uploadRoot);
            String safeName = java.util.UUID.randomUUID() + "-" + upload.getOriginalFilename();
            Path target = uploadRoot.resolve(safeName).normalize();
            upload.transferTo(target);
            OrderFile file = new OrderFile();
            file.orderId = orderId;
            file.fileName = upload.getOriginalFilename();
            file.filePath = target.toString();
            file.sizeBytes = upload.getSize();
            file.fileStatus = "UPLOADED";
            file.uploadedAt = now();
            order.currentStep = "文件已上传，等待文件检查";
            order.updatedAt = now();
            orders.save(order);
            audit.record(username, "ORD", "UPLOAD_FILE", "ORDER_FILE", orderId, file.fileName);
            return files.save(file);
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "文件保存失败：" + ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<OrderFile> orderFiles(String username, Long orderId) {
        access.requireVisibleOrder(identityService.requireUser(username), orderId);
        return files.findByOrderIdOrderByUploadedAtDesc(orderId);
    }

    public PrintOrder requireVisibleOrder(String username, Long id) {
        return access.requireVisibleOrder(identityService.requireUser(username), id);
    }

    public PrintOrder saveOrder(PrintOrder order) {
        return orders.save(order);
    }

    private void applyOrderUpdate(UserAccount user, PrintOrder order, PrintOrder request) {
        Set<String> allowed = access.editableOrderFields(user);
        boolean hasPricingChange = hasAnyOrderField(request, "productType", "colorMode", "pageCount", "copies", "deliveryMode", "priority");
        if ("CUSTOMER".equals(user.role) && hasPricingChange && !OrderAccessPolicy.EARLY_ORDER_STATUSES.contains(order.status)) {
            throw forbidden("订单已进入处理流程，客户不能再修改订单规格。");
        }
        assertNoForbiddenOrderFields(order, request, allowed);

        if (allowed.contains("productType") && request.productType != null) {
            order.productType = pricing.normalizeOption(request.productType, OrderPricingPolicy.PRODUCT_TYPES, "产品类型");
        }
        if (allowed.contains("colorMode") && request.colorMode != null) {
            order.colorMode = pricing.normalizeOption(request.colorMode, OrderPricingPolicy.COLOR_MODES, "颜色/工艺");
        }
        if (allowed.contains("pageCount") && request.pageCount != null) {
            pricing.validatePositive(request.pageCount, "页数");
            order.pageCount = request.pageCount;
        }
        if (allowed.contains("copies") && request.copies != null) {
            pricing.validatePositive(request.copies, "份数");
            order.copies = request.copies;
        }
        if (allowed.contains("dueAt") && request.dueAt != null) {
            order.dueAt = text(request.dueAt, order.dueAt);
        }
        if (allowed.contains("deliveryMode") && request.deliveryMode != null) {
            order.deliveryMode = pricing.normalizeOption(request.deliveryMode, OrderPricingPolicy.DELIVERY_MODES, "交付方式");
        }
        if (allowed.contains("priority") && request.priority != null) {
            order.priority = pricing.normalizeOption(request.priority, OrderPricingPolicy.PRIORITIES, "优先级");
        }
        if (allowed.contains("status") && request.status != null) {
            if (!Objects.equals(request.status, order.status)) {
                statusPolicy.assertManualTransition(order, request.status);
            }
            order.status = text(request.status, order.status);
        }
        if (allowed.contains("paymentStatus") && request.paymentStatus != null) {
            order.paymentStatus = text(request.paymentStatus, order.paymentStatus);
        }
        if (allowed.contains("currentStep") && request.currentStep != null) {
            order.currentStep = text(request.currentStep, order.currentStep);
        }
        if (hasPricingChange) {
            order.totalAmount = pricing.calculate(order);
        }
    }

    private void assertNoForbiddenOrderFields(PrintOrder order, PrintOrder request, Set<String> allowed) {
        assertOrderFieldAllowed(allowed, "customerId", request.customerId, order.customerId);
        assertOrderFieldAllowed(allowed, "customerName", request.customerName, order.customerName);
        assertOrderFieldAllowed(allowed, "storeId", request.storeId, order.storeId);
        assertOrderFieldAllowed(allowed, "storeName", request.storeName, order.storeName);
        assertOrderFieldAllowed(allowed, "dueAt", request.dueAt, order.dueAt);
        assertOrderFieldAllowed(allowed, "status", request.status, order.status);
        assertOrderFieldAllowed(allowed, "paymentStatus", request.paymentStatus, order.paymentStatus);
        assertOrderFieldAllowed(allowed, "currentStep", request.currentStep, order.currentStep);
        assertForbiddenIfMissing(allowed, "productType", request.productType, "产品类型");
        assertForbiddenIfMissing(allowed, "colorMode", request.colorMode, "颜色/工艺");
        assertForbiddenIfMissing(allowed, "pageCount", request.pageCount, "页数");
        assertForbiddenIfMissing(allowed, "copies", request.copies, "份数");
        assertForbiddenIfMissing(allowed, "deliveryMode", request.deliveryMode, "交付方式");
        assertForbiddenIfMissing(allowed, "priority", request.priority, "优先级");
    }

    private void assertOrderFieldAllowed(Set<String> allowed, String field, Object requested, Object current) {
        if (requested != null && !allowed.contains(field) && !Objects.equals(requested, current)) {
            throw forbidden("当前角色不能修改字段：" + field);
        }
    }

    private void assertForbiddenIfMissing(Set<String> allowed, String field, Object requested, String label) {
        if (!allowed.contains(field) && requested != null) {
            throw forbidden("当前角色不能修改" + label + "。");
        }
    }

    private boolean hasAnyOrderField(PrintOrder request, String... fields) {
        for (String field : fields) {
            if (switch (field) {
                case "productType" -> request.productType != null;
                case "colorMode" -> request.colorMode != null;
                case "pageCount" -> request.pageCount != null;
                case "copies" -> request.copies != null;
                case "deliveryMode" -> request.deliveryMode != null;
                case "priority" -> request.priority != null;
                default -> false;
            }) {
                return true;
            }
        }
        return false;
    }
}
