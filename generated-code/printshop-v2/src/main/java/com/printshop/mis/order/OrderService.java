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
import com.printshop.mis.order.OrderFileAnalysisService.AnalysisResult;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class OrderService {

    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final long MAX_UPLOAD_BYTES = 50L * 1024L * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png", "doc", "docx", "psd", "ai");
    private static final Set<String> PREVIEW_CONTENT_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");

    private final IdentityService identityService;
    private final PrintOrderRepository orders;
    private final OrderFileRepository files;
    private final AuditTrailService audit;
    private final OrderPricingPolicy pricing;
    private final OrderAccessPolicy access;
    private final OrderStatusPolicy statusPolicy;
    private final OrderFileAnalysisService fileAnalysis;
    private final Path uploadRoot;

    public OrderService(
            IdentityService identityService,
            PrintOrderRepository orders,
            OrderFileRepository files,
            AuditTrailService audit,
            OrderPricingPolicy pricing,
            OrderAccessPolicy access,
            OrderStatusPolicy statusPolicy,
            OrderFileAnalysisService fileAnalysis,
            @Value("${printshop.upload-dir:uploads}") String uploadDir
    ) {
        this.identityService = identityService;
        this.orders = orders;
        this.files = files;
        this.audit = audit;
        this.pricing = pricing;
        this.access = access;
        this.statusPolicy = statusPolicy;
        this.fileAnalysis = fileAnalysis;
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
        pricing.validateOrderNumbers(order.pageCount, order.copies);
        order.sizeName = text(request.sizeName, "未指定尺寸");
        order.paperType = text(request.paperType, "标准纸");
        order.craftType = text(request.craftType, "无特殊工艺");
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

    public CreatedOrderFromFile createOrderFromFile(String username, MultipartFile upload) {
        validateUpload(upload);
        PrintOrder order = createOrder(username, new PrintOrder());
        OrderFile file = uploadFile(username, order.id, upload);
        return new CreatedOrderFromFile(order, file);
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
        requireFileBeforeReview(order, requestedStatus);
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
        UserAccount user = identityService.requireUser(username);
        PrintOrder order = access.requireVisibleOrder(user, orderId);
        validateUpload(upload);
        Path target = null;
        try {
            Files.createDirectories(uploadRoot);
            String originalName = safeOriginalName(upload.getOriginalFilename());
            String extension = extensionOf(originalName);
            String storageName = UUID.randomUUID() + "." + extension;
            target = uploadRoot.resolve(storageName).normalize();
            if (!target.startsWith(uploadRoot)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "文件名不合法。");
            }
            upload.transferTo(target);
            OrderFile file = new OrderFile();
            file.orderId = orderId;
            file.fileName = originalName;
            file.filePath = target.toString();
            file.contentType = resolveContentType(upload, extension);
            file.storageName = storageName;
            file.sizeBytes = upload.getSize();
            file.fileStatus = "UPLOADED";
            file.versionNo = Math.toIntExact(files.countByOrderId(orderId) + 1);
            file.uploadedBy = user.displayName;
            file.uploadedRole = user.role;
            file.reviewStatus = "PENDING";
            AnalysisResult analysis = fileAnalysis.analyze(target, extension);
            applyAnalysis(file, analysis);
            file.uploadedAt = now();
            applyAnalysisToOrder(username, order, file);
            order.updatedAt = now();
            orders.save(order);
            OrderFile saved = files.save(file);
            audit.record(username, "ORD", "UPLOAD_FILE", "ORDER_FILE", orderId, file.fileName);
            audit.record(username, "ORD", "ANALYZE_FILE", "ORDER_FILE", saved.id,
                    file.analysisStatus + " - " + file.analysisMessage);
            return saved;
        } catch (IOException ex) {
            deleteStoredFile(target);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "文件保存失败：" + ex.getMessage());
        } catch (RuntimeException | Error ex) {
            deleteStoredFile(target);
            throw ex;
        }
    }

    public OrderFile attachGeneratedFile(String username, Long orderId, String fileName, String contentType, byte[] content) {
        UserAccount user = identityService.requireUser(username);
        PrintOrder order = access.requireVisibleOrder(user, orderId);
        Path target = null;
        try {
            Files.createDirectories(uploadRoot);
            String originalName = safeOriginalName(fileName == null ? "online-design.pdf" : fileName);
            String extension = extensionOf(originalName);
            String storageName = UUID.randomUUID() + "." + text(extension, "pdf");
            target = uploadRoot.resolve(storageName).normalize();
            if (!target.startsWith(uploadRoot)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "文件名不合法。");
            }
            byte[] safeContent = content == null || content.length == 0
                    ? "%PDF-1.4\n% PrintShop generated design placeholder\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)
                    : content;
            Files.write(target, safeContent);

            OrderFile file = new OrderFile();
            file.orderId = orderId;
            file.fileName = originalName;
            file.filePath = target.toString();
            file.contentType = text(contentType, "application/pdf");
            file.storageName = storageName;
            file.sizeBytes = (long) safeContent.length;
            file.fileStatus = "GENERATED";
            file.versionNo = Math.toIntExact(files.countByOrderId(orderId) + 1);
            file.uploadedBy = user.displayName;
            file.uploadedRole = user.role;
            file.reviewStatus = "PENDING";
            file.analysisStatus = "GENERATED";
            file.analysisMessage = "在线设计编辑器生成的可审核设计稿。";
            file.analyzedAt = now();
            file.uploadedAt = now();
            order.currentStep = "在线设计稿已生成，等待门店审核文件";
            order.updatedAt = now();
            orders.save(order);
            OrderFile saved = files.save(file);
            audit.record(username, "ORD", "GENERATE_DESIGN_FILE", "ORDER_FILE", saved.id, file.fileName);
            return saved;
        } catch (IOException ex) {
            deleteStoredFile(target);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "设计稿保存失败：" + ex.getMessage());
        } catch (RuntimeException | Error ex) {
            deleteStoredFile(target);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<OrderFile> orderFiles(String username, Long orderId) {
        access.requireVisibleOrder(identityService.requireUser(username), orderId);
        return files.findByOrderIdOrderByUploadedAtDesc(orderId);
    }

    public StoredFile downloadFile(String username, Long fileId) {
        UserAccount user = identityService.requireUser(username);
        OrderFile file = requireVisibleFile(user, fileId);
        audit.record(username, "ORD", "DOWNLOAD_FILE", "ORDER_FILE", file.id, file.fileName);
        return storedFile(file, false);
    }

    public StoredFile previewFile(String username, Long fileId) {
        UserAccount user = identityService.requireUser(username);
        OrderFile file = requireVisibleFile(user, fileId);
        String contentType = text(file.contentType, "application/octet-stream").toLowerCase(Locale.ROOT);
        if (!PREVIEW_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该文件类型暂不支持在线预览，请下载查看。");
        }
        audit.record(username, "ORD", "PREVIEW_FILE", "ORDER_FILE", file.id, file.fileName);
        return storedFile(file, true);
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
            pricing.validateOrderNumbers(request.pageCount, request.copies == null ? order.copies : request.copies);
            order.pageCount = request.pageCount;
        }
        if (allowed.contains("copies") && request.copies != null) {
            pricing.validateOrderNumbers(request.pageCount == null ? order.pageCount : request.pageCount, request.copies);
            order.copies = request.copies;
        }
        if (allowed.contains("sizeName") && request.sizeName != null) {
            order.sizeName = text(request.sizeName, order.sizeName);
        }
        if (allowed.contains("paperType") && request.paperType != null) {
            order.paperType = text(request.paperType, order.paperType);
        }
        if (allowed.contains("craftType") && request.craftType != null) {
            order.craftType = text(request.craftType, order.craftType);
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
                requireFileBeforeReview(order, request.status);
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
        assertForbiddenIfMissing(allowed, "sizeName", request.sizeName, "尺寸");
        assertForbiddenIfMissing(allowed, "paperType", request.paperType, "纸张");
        assertForbiddenIfMissing(allowed, "craftType", request.craftType, "工艺");
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

    private OrderFile requireVisibleFile(UserAccount user, Long fileId) {
        OrderFile file = files.findById(fileId).orElseThrow(() -> com.printshop.mis.shared.MisSupport.notFound("订单文件", fileId));
        access.requireVisibleOrder(user, file.orderId);
        return file;
    }

    private void applyAnalysis(OrderFile file, AnalysisResult analysis) {
        file.analysisStatus = analysis.status();
        file.detectedPageCount = analysis.pageCount();
        file.detectedWidthMm = analysis.widthMm();
        file.detectedHeightMm = analysis.heightMm();
        file.detectedPixelWidth = analysis.pixelWidth();
        file.detectedPixelHeight = analysis.pixelHeight();
        file.detectedDpiX = analysis.dpiX();
        file.detectedDpiY = analysis.dpiY();
        file.mixedPageSizes = analysis.mixedPageSizes();
        file.analysisMessage = analysis.message();
        file.analyzedAt = now();
    }

    private void applyAnalysisToOrder(String username, PrintOrder order, OrderFile file) {
        Integer detectedPages = file.detectedPageCount;
        boolean validPageCount = detectedPages != null && detectedPages >= 1 && detectedPages <= 5000;
        if ("DETECTED".equals(file.analysisStatus) && validPageCount && OrderStatusPolicy.SUBMITTED.equals(order.status)) {
            Integer oldPageCount = order.pageCount;
            order.pageCount = detectedPages;
            order.totalAmount = pricing.calculate(order);
            order.currentStep = "已识别 " + detectedPages + " 页并更新订单金额，等待文件检查";
            file.analysisMessage += " 订单页数已由 " + oldPageCount + " 更新为 " + detectedPages + "，金额已重新计算。";
            audit.record(username, "ORD", "AUTO_UPDATE_ORDER_FROM_FILE", "ORDER", order.id,
                    "pageCount " + oldPageCount + " -> " + detectedPages + ", fileVersion=" + file.versionNo);
            return;
        }
        if ("DETECTED".equals(file.analysisStatus) && validPageCount) {
            order.currentStep = "文件已上传并识别 " + detectedPages + " 页，订单已进入审核，未自动修改";
            file.analysisMessage += " 订单已进入审核，未自动修改订单参数。";
            return;
        }
        if ("DETECTED".equals(file.analysisStatus)) {
            order.currentStep = "文件已上传，识别页数超出订单范围，请人工检查";
            return;
        }
        if ("UNSUPPORTED".equals(file.analysisStatus)) {
            order.currentStep = "文件已上传，暂不支持自动识别，请人工检查";
            return;
        }
        if ("PARTIAL".equals(file.analysisStatus)) {
            order.currentStep = "文件已上传，已读取部分参数，请人工确认页数";
            return;
        }
        order.currentStep = "文件已上传，自动识别失败，请人工检查";
    }

    private void deleteStoredFile(Path target) {
        if (target == null || !target.startsWith(uploadRoot)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // Preserve the original upload or persistence error.
        }
    }

    private void requireFileBeforeReview(PrintOrder order, String requestedStatus) {
        if (OrderStatusPolicy.REVIEWING.equals(requestedStatus)
                && !OrderStatusPolicy.REVIEWING.equals(order.status)
                && !files.existsByOrderId(order.id)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请先上传订单文件，再提交审核。");
        }
    }

    private StoredFile storedFile(OrderFile file, boolean inline) {
        Path path = Path.of(text(file.filePath, uploadRoot.resolve(text(file.storageName, "")).toString())).toAbsolutePath().normalize();
        if (!path.startsWith(uploadRoot) || !Files.exists(path) || !Files.isRegularFile(path)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "文件实体不存在或已被移除。");
        }
        try {
            Resource resource = new UrlResource(path.toUri());
            return new StoredFile(file, resource, text(file.contentType, "application/octet-stream"), inline);
        } catch (MalformedURLException ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "文件地址无效：" + ex.getMessage());
        }
    }

    private void validateUpload(MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "上传文件不能为空。");
        }
        if (upload.getSize() > MAX_UPLOAD_BYTES) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文件不能超过 50MB。");
        }
        String extension = extensionOf(safeOriginalName(upload.getOriginalFilename()));
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "不支持的文件类型：" + extension);
        }
    }

    private String safeOriginalName(String originalName) {
        String name = originalName == null ? "upload.bin" : Path.of(originalName).getFileName().toString();
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return name.isBlank() ? "upload.bin" : name;
    }

    private String extensionOf(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String resolveContentType(MultipartFile upload, String extension) {
        String contentType = upload.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            return contentType;
        }
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "application/octet-stream";
        };
    }

    public record CreatedOrderFromFile(PrintOrder order, OrderFile file) {
    }

    public record StoredFile(OrderFile file, Resource resource, String contentType, boolean inline) {
    }
}
