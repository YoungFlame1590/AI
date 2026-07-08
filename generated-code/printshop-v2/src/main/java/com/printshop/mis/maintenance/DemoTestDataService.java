package com.printshop.mis.maintenance;

import com.printshop.common.exception.BusinessException;
import com.printshop.mis.delivery.DeliveryService;
import com.printshop.mis.domain.DeliveryQuote;
import com.printshop.mis.domain.InventoryConsumption;
import com.printshop.mis.domain.InventoryItem;
import com.printshop.mis.domain.PrintOrder;
import com.printshop.mis.domain.UserAccount;
import com.printshop.mis.feedback.FeedbackService;
import com.printshop.mis.identity.IdentityService;
import com.printshop.mis.inventory.InventoryService;
import com.printshop.mis.order.OrderWorkflowService;
import com.printshop.mis.order.OrderService;
import com.printshop.mis.replenishment.ReplenishmentService;
import com.printshop.mis.reporting.ReportingService;
import com.printshop.mis.repository.ComplaintTicketRepository;
import com.printshop.mis.repository.InventoryConsumptionRepository;
import com.printshop.mis.repository.PrintOrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DemoTestDataService {

    private static final List<String> STAGES = List.of(
            "SUBMITTED", "REVIEWING", "QUOTED", "JOB_READY",
            "IN_PRODUCTION", "PRODUCTION_DONE", "DELIVERING", "DONE"
    );
    private static final List<StoreProfile> PROFILES = List.of(
            new StoreProfile("A", "大学城店", "customer", "clerk"),
            new StoreProfile("B", "市中心店", "customer_b", "clerk_b"),
            new StoreProfile("C", "西区店", "customer_c", "clerk_c")
    );
    private static final Map<String, BigDecimal> DEMO_INVENTORY_LEVELS = Map.of(
            "PAPER-A4-80G", new BigDecimal("4000"),
            "PAPER-COATED-300G", new BigDecimal("150"),
            "INK-COLOR", new BigDecimal("120"),
            "BINDING-CONSUMABLE", new BigDecimal("60")
    );
    private static final byte[] DEMO_FILE = "%PDF-1.4\n% CR09 one-click demo placeholder\n"
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);

    private final IdentityService identityService;
    private final BusinessDataMaintenanceService maintenanceService;
    private final InventoryService inventoryService;
    private final InventoryConsumptionRepository consumptions;
    private final OrderService orderService;
    private final OrderWorkflowService workflowService;
    private final DeliveryService deliveryService;
    private final FeedbackService feedbackService;
    private final ReplenishmentService replenishmentService;
    private final ReportingService reportingService;
    private final PrintOrderRepository orders;
    private final ComplaintTicketRepository complaints;

    public DemoTestDataService(
            IdentityService identityService,
            BusinessDataMaintenanceService maintenanceService,
            InventoryService inventoryService,
            InventoryConsumptionRepository consumptions,
            OrderService orderService,
            OrderWorkflowService workflowService,
            DeliveryService deliveryService,
            FeedbackService feedbackService,
            ReplenishmentService replenishmentService,
            ReportingService reportingService,
            PrintOrderRepository orders,
            ComplaintTicketRepository complaints
    ) {
        this.identityService = identityService;
        this.maintenanceService = maintenanceService;
        this.inventoryService = inventoryService;
        this.consumptions = consumptions;
        this.orderService = orderService;
        this.workflowService = workflowService;
        this.deliveryService = deliveryService;
        this.feedbackService = feedbackService;
        this.replenishmentService = replenishmentService;
        this.reportingService = reportingService;
        this.orders = orders;
        this.complaints = complaints;
    }

    public Map<String, Object> seed(String username, Map<String, Object> request) {
        UserAccount admin = identityService.requireUser(username);
        if (!"ADMIN".equals(admin.role)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只有系统管理员可以执行一键测试。");
        }
        int requestedOrders = Math.max(8, Math.min(120, number(request.get("orders"), 24)));
        boolean clear = booleanValue(request.get("clear"), true);
        if (clear) {
            maintenanceService.clearBusinessData(username);
        }
        ensureDemoAccountsExist();
        seedHistoricalConsumption();
        setDemoInventoryLevels(username, expectedProductionConsumption(requestedOrders));

        Map<String, Integer> stageDistribution = new LinkedHashMap<>();
        STAGES.forEach(stage -> stageDistribution.put(stage, 0));
        int reviewedOrders = 0;
        for (int index = 0; index < requestedOrders; index++) {
            StoreProfile profile = PROFILES.get(index % PROFILES.size());
            String stage = STAGES.get(index % STAGES.size());
            PrintOrder order = orderService.createOrder(profile.customer(), orderPayload(index));
            orderService.attachGeneratedFile(profile.customer(), order.id, "cr09-demo-order.pdf", "application/pdf", DEMO_FILE);
            stepOrderToStage(order.id, stage, profile);
            if ("DONE".equals(stage)) {
                feedbackService.submitReview(profile.customer(), order.id, reviewPayload(index, profile));
                reviewedOrders++;
            }
            stageDistribution.compute(stage, (ignored, count) -> count == null ? 1 : count + 1);
        }
        setDemoInventoryLevels(username, Map.of());

        List<Map<String, Object>> ranking = reportingService.storeQualityRanking(username);
        List<Map<String, Object>> recommendations = replenishmentService.recommendations(username);
        List<Map<String, Object>> forecast = replenishmentService.forecastNextThirtyDays(username);
        return Map.of(
                "message", "CR09 一键测试数据已生成。",
                "requestedOrders", requestedOrders,
                "stageDistribution", stageDistribution,
                "reviewedOrders", reviewedOrders,
                "complaintCount", complaints.count(),
                "orderCount", orders.count(),
                "storeQualityRanking", ranking,
                "replenishmentRecommendations", recommendations,
                "replenishmentForecast", forecast
        );
    }

    private void stepOrderToStage(Long orderId, String stage, StoreProfile profile) {
        if ("SUBMITTED".equals(stage)) {
            return;
        }
        workflowService.executeAction(profile.customer(), orderId, "SUBMIT_REVIEW", Map.of());
        if ("REVIEWING".equals(stage)) {
            return;
        }
        workflowService.executeAction(profile.clerk(), orderId, "QUOTE", Map.of());
        if ("QUOTED".equals(stage)) {
            return;
        }
        workflowService.executeAction(profile.customer(), orderId, "CONFIRM_QUOTE", Map.of());
        workflowService.executeAction(profile.clerk(), orderId, "JOB_TICKET", Map.of());
        if ("JOB_READY".equals(stage)) {
            return;
        }
        workflowService.executeAction("ops", orderId, "SCHEDULE_PRODUCTION", Map.of());
        if ("IN_PRODUCTION".equals(stage)) {
            return;
        }
        workflowService.executeAction("ops", orderId, "COMPLETE_PRODUCTION", Map.of());
        if ("PRODUCTION_DONE".equals(stage)) {
            return;
        }
        createDeliveryForDemoOrder(orderId, profile);
        if ("DELIVERING".equals(stage)) {
            return;
        }
        workflowService.executeAction("courier", orderId, "ACCEPT_DELIVERY", Map.of());
        workflowService.executeAction("courier", orderId, "SIGN_DELIVERY", Map.of("signedBy", profile.storeName() + "测试客户"));
    }

    private void createDeliveryForDemoOrder(Long orderId, StoreProfile profile) {
        PrintOrder order = orders.findById(orderId)
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "演示订单不存在：" + orderId));
        if (Set.of("同城配送", "外协配送").contains(order.deliveryMode)) {
            DeliveryQuote quote = deliveryService.createDeliveryQuote("ops", Map.of(
                    "orderId", orderId,
                    "channelCode", "外协配送".equals(order.deliveryMode) ? "EXPRESS" : "IMMEDIATE",
                    "pickupAddress", order.storeName == null ? profile.storeName() : order.storeName,
                    "deliveryAddress", "广州市" + profile.storeName() + "演示客户地址",
                    "packageWeightKg", "1.5"
            ));
            deliveryService.confirmDeliveryQuote("ops", quote.id);
            return;
        }
        workflowService.executeAction("ops", orderId, "CREATE_DELIVERY", Map.of());
    }

    private PrintOrder orderPayload(int index) {
        List<String> productTypes = List.of("论文胶装", "培训手册", "名片快印", "海报写真", "宣传单页", "写真展板");
        List<String> colorModes = List.of("黑白", "彩色", "黑白加彩页", "覆膜", "装订加覆膜");
        List<String> deliveryModes = List.of("到店自提", "同城配送", "跨店配送", "外协配送");
        List<String> priorities = List.of("普通", "加急", "特急");
        PrintOrder order = new PrintOrder();
        order.productType = productTypes.get(index % productTypes.size());
        order.colorMode = colorModes.get((index * 2) % colorModes.size());
        order.pageCount = 8 + ((index * 7) % 160);
        order.copies = 1 + ((index * 3) % 80);
        order.deliveryMode = deliveryModes.get((index * 3) % deliveryModes.size());
        order.priority = priorities.get(index % priorities.size());
        return order;
    }

    private Map<String, Object> reviewPayload(int index, StoreProfile profile) {
        if ("B".equals(profile.key())) {
            return Map.of(
                    "printQualityRating", 5,
                    "timelinessRating", 5,
                    "staffRating", 5,
                    "valueRating", 4 + (index % 2),
                    "comment", "市中心店响应快，交付稳定，适合作为高分演示样本。"
            );
        }
        if ("C".equals(profile.key())) {
            if (index % 5 == 3 || index % 5 == 0) {
                return Map.of(
                        "printQualityRating", 2,
                        "timelinessRating", 1,
                        "staffRating", 2,
                        "valueRating", 2,
                        "comment", "交付等待时间偏长，沟通需要改进。"
                );
            }
            return Map.of(
                    "printQualityRating", 3,
                    "timelinessRating", 3,
                    "staffRating", 3,
                    "valueRating", 3,
                    "comment", "西区店本次完成交付，但体验一般。"
            );
        }
        return Map.of(
                "printQualityRating", 4,
                "timelinessRating", 4,
                "staffRating", 4,
                "valueRating", 3 + (index % 2),
                "comment", "大学城店整体稳定，作为中等偏上对照组。"
        );
    }

    private void seedHistoricalConsumption() {
        List<InventoryConsumption> oldDemo = consumptions.findAll().stream()
                .filter(item -> item.orderNo != null && item.orderNo.startsWith("ONEKEY-HIS-"))
                .toList();
        if (!oldDemo.isEmpty()) {
            consumptions.deleteAllInBatch(oldDemo);
        }
        YearMonth start = YearMonth.now().minusMonths(12);
        for (int monthIndex = 0; monthIndex <= 12; monthIndex++) {
            YearMonth month = start.plusMonths(monthIndex);
            addMonthlyConsumption("PAPER-A4-80G", "A4 80g 复印纸", month, monthIndex, a4Total(month, monthIndex));
            addMonthlyConsumption("PAPER-COATED-300G", "300g 铜版纸", month, monthIndex, coatedTotal(monthIndex));
            addMonthlyConsumption("INK-COLOR", "彩色墨粉/墨水", month, monthIndex, inkTotal(monthIndex));
            addMonthlyConsumption("BINDING-CONSUMABLE", "装订耗材", month, monthIndex, bindingTotal(monthIndex));
        }
    }

    private void addMonthlyConsumption(String sku, String itemName, YearMonth month, int monthIndex, BigDecimal total) {
        BigDecimal[] shares = {
                new BigDecimal("0.42"),
                new BigDecimal("0.35"),
                new BigDecimal("0.23")
        };
        for (int storeIndex = 0; storeIndex < PROFILES.size(); storeIndex++) {
            StoreProfile profile = PROFILES.get(storeIndex);
            InventoryConsumption consumption = new InventoryConsumption();
            consumption.sku = sku;
            consumption.itemName = itemName;
            consumption.quantity = total.multiply(shares[storeIndex]).setScale(2, RoundingMode.HALF_UP);
            consumption.storeName = profile.storeName();
            consumption.orderNo = "ONEKEY-HIS-" + sku + "-" + month + "-" + profile.key();
            consumption.consumedAt = consumptionDate(month, storeIndex);
            consumptions.save(consumption);
        }
    }

    private LocalDateTime consumptionDate(YearMonth month, int storeIndex) {
        int[] days = {4, 14, 24};
        int day = Math.min(days[storeIndex], month.lengthOfMonth());
        YearMonth currentMonth = YearMonth.now();
        if (month.equals(currentMonth)) {
            day = Math.min(day, Math.max(1, LocalDate.now().getDayOfMonth()));
        }
        return month.atDay(day).atTime(9 + storeIndex * 3, 20);
    }

    private BigDecimal a4Total(YearMonth month, int index) {
        BigDecimal base = new BigDecimal("4200").add(BigDecimal.valueOf(index * 250L));
        if (month.getMonthValue() == 9) {
            base = base.multiply(new BigDecimal("1.90"));
        }
        return base;
    }

    private BigDecimal coatedTotal(int index) {
        int wave = switch (index % 4) {
            case 1 -> 10;
            case 2 -> 18;
            case 3 -> -6;
            default -> 0;
        };
        return new BigDecimal("1800").add(BigDecimal.valueOf(wave));
    }

    private BigDecimal inkTotal(int index) {
        return new BigDecimal("300").multiply(BigDecimal.valueOf(Math.pow(1.03, index))).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal bindingTotal(int index) {
        return new BigDecimal("650").subtract(BigDecimal.valueOf(index * 10L)).max(new BigDecimal("400"));
    }

    private void setDemoInventoryLevels(String username, Map<String, BigDecimal> additionalRequired) {
        inventoryService.ensureDefaultInventory();
        for (InventoryItem item : inventoryService.inventoryItems()) {
            BigDecimal target = DEMO_INVENTORY_LEVELS.get(item.sku);
            if (target != null) {
                BigDecimal additional = additionalRequired.getOrDefault(item.sku, BigDecimal.ZERO);
                inventoryService.setInventoryQuantity(username, item.id, target.add(additional));
            }
        }
    }

    private Map<String, BigDecimal> expectedProductionConsumption(int requestedOrders) {
        Map<String, BigDecimal> required = new LinkedHashMap<>();
        for (int index = 0; index < requestedOrders; index++) {
            String stage = STAGES.get(index % STAGES.size());
            if (!Set.of("PRODUCTION_DONE", "DELIVERING", "DONE").contains(stage)) {
                continue;
            }
            PrintOrder order = orderPayload(index);
            requiredMaterials(order.productType, order.colorMode, order.pageCount, order.copies)
                    .forEach((sku, quantity) -> required.merge(sku, quantity, BigDecimal::add));
        }
        return required;
    }

    private Map<String, BigDecimal> requiredMaterials(String productType, String colorMode, Integer pageCount, Integer copies) {
        int safePages = Math.max(1, pageCount == null ? 1 : pageCount);
        int safeCopies = Math.max(1, copies == null ? 1 : copies);
        Map<String, BigDecimal> required = new LinkedHashMap<>();
        String paperSku = Set.of("名片快印", "海报写真", "写真展板").contains(productType)
                ? "PAPER-COATED-300G"
                : "PAPER-A4-80G";
        required.merge(paperSku, BigDecimal.valueOf((long) safePages * safeCopies), BigDecimal::add);
        if (!"黑白".equals(colorMode)) {
            required.merge("INK-COLOR", BigDecimal.valueOf(safeCopies), BigDecimal::add);
        }
        if (Set.of("论文胶装", "培训手册").contains(productType) || "装订加覆膜".equals(colorMode)) {
            required.merge("BINDING-CONSUMABLE", BigDecimal.valueOf(safeCopies), BigDecimal::add);
        }
        return required;
    }

    private void ensureDemoAccountsExist() {
        for (StoreProfile profile : PROFILES) {
            identityService.requireUser(profile.customer());
            identityService.requireUser(profile.clerk());
        }
        identityService.requireUser("ops");
        identityService.requireUser("courier");
    }

    private int number(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private boolean booleanValue(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private record StoreProfile(String key, String storeName, String customer, String clerk) {
    }
}
