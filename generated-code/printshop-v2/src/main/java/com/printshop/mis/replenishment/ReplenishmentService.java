package com.printshop.mis.replenishment;

import static com.printshop.mis.shared.MisSupport.code;
import static com.printshop.mis.shared.MisSupport.forbidden;
import static com.printshop.mis.shared.MisSupport.notFound;
import static com.printshop.mis.shared.MisSupport.now;
import static com.printshop.mis.shared.MisSupport.text;

import com.printshop.common.exception.BusinessException;
import com.printshop.mis.audit.AuditTrailService;
import com.printshop.mis.domain.InventoryConsumption;
import com.printshop.mis.domain.InventoryItem;
import com.printshop.mis.domain.PurchaseSuggestion;
import com.printshop.mis.domain.SupplierProfile;
import com.printshop.mis.domain.UserAccount;
import com.printshop.mis.identity.IdentityService;
import com.printshop.mis.repository.InventoryConsumptionRepository;
import com.printshop.mis.repository.InventoryItemRepository;
import com.printshop.mis.repository.PurchaseSuggestionRepository;
import com.printshop.mis.repository.SupplierProfileRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ReplenishmentService {

    private static final BigDecimal BUFFER_DAYS = new BigDecimal("7");

    private final IdentityService identityService;
    private final InventoryItemRepository inventoryItems;
    private final InventoryConsumptionRepository consumptions;
    private final SupplierProfileRepository suppliers;
    private final PurchaseSuggestionRepository suggestions;
    private final AuditTrailService audit;

    public ReplenishmentService(
            IdentityService identityService,
            InventoryItemRepository inventoryItems,
            InventoryConsumptionRepository consumptions,
            SupplierProfileRepository suppliers,
            PurchaseSuggestionRepository suggestions,
            AuditTrailService audit
    ) {
        this.identityService = identityService;
        this.inventoryItems = inventoryItems;
        this.consumptions = consumptions;
        this.suppliers = suppliers;
        this.suggestions = suggestions;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> recommendations(String username) {
        requireOps(username, false);
        return inventoryItems.findAll().stream().map(this::recommendationFor).toList();
    }

    public List<PurchaseSuggestion> recalculate(String username) {
        requireOps(username, true);
        ensureDefaultSuppliers();
        List<PurchaseSuggestion> created = inventoryItems.findAll().stream()
                .map(this::suggestionFor)
                .filter(item -> item.recommendedQuantity.compareTo(BigDecimal.ZERO) > 0)
                .map(suggestions::save)
                .toList();
        audit.record(username, "INV", "RECALCULATE_REPLENISHMENT", "PURCHASE_SUGGESTION", null, "created=" + created.size());
        return created;
    }

    @Transactional(readOnly = true)
    public List<PurchaseSuggestion> purchaseSuggestions(String username) {
        requireOps(username, false);
        return suggestions.findAllByOrderByCreatedAtDesc();
    }

    public PurchaseSuggestion approveSuggestion(String username, Long id) {
        UserAccount user = requireOps(username, true);
        PurchaseSuggestion suggestion = suggestions.findById(id).orElseThrow(() -> notFound("采购建议", id));
        if (!"PENDING".equals(suggestion.status)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "采购建议已处理。");
        }
        suggestion.status = "APPROVED";
        suggestion.approvedBy = user.displayName;
        suggestion.approvedAt = now();
        audit.record(username, "INV", "APPROVE_PURCHASE_SUGGESTION", "PURCHASE_SUGGESTION", id, suggestion.sku);
        return suggestions.save(suggestion);
    }

    public void ensureDefaultSuppliers() {
        ensureSupplier("PAPER-A4-80G", "总部纸张集采供应商", 3, "500", "0.05", "500:1.00,2000:0.95,5000:0.90");
        ensureSupplier("PAPER-COATED-300G", "铜版纸区域供应商", 5, "200", "0.18", "200:1.00,1000:0.93,3000:0.88");
        ensureSupplier("INK-COLOR", "彩色耗材供应商", 4, "50", "1.20", "50:1.00,200:0.96,500:0.91");
        ensureSupplier("BINDING-CONSUMABLE", "装订耗材供应商", 2, "80", "0.35", "80:1.00,300:0.94,800:0.89");
    }

    private Map<String, Object> recommendationFor(InventoryItem item) {
        SupplierProfile supplier = supplierFor(item.sku);
        BigDecimal dynamicSafetyStock = dynamicSafetyStock(item, supplier);
        BigDecimal recommended = recommendedQuantity(item, supplier, dynamicSafetyStock);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sku", item.sku);
        result.put("itemName", item.itemName);
        result.put("quantity", item.quantity);
        result.put("staticSafetyStock", item.safetyStock);
        result.put("dynamicSafetyStock", dynamicSafetyStock);
        result.put("recommendedQuantity", recommended);
        result.put("supplierName", supplier.supplierName);
        result.put("estimatedCost", costFor(supplier, recommended));
        result.put("reason", reason(item, supplier, dynamicSafetyStock, recommended));
        return result;
    }

    private PurchaseSuggestion suggestionFor(InventoryItem item) {
        SupplierProfile supplier = supplierFor(item.sku);
        BigDecimal dynamicSafetyStock = dynamicSafetyStock(item, supplier);
        BigDecimal recommended = recommendedQuantity(item, supplier, dynamicSafetyStock);
        PurchaseSuggestion suggestion = new PurchaseSuggestion();
        suggestion.suggestionNo = code("PUR");
        suggestion.sku = item.sku;
        suggestion.itemName = item.itemName;
        suggestion.currentQuantity = item.quantity == null ? BigDecimal.ZERO : item.quantity;
        suggestion.dynamicSafetyStock = dynamicSafetyStock;
        suggestion.recommendedQuantity = recommended;
        suggestion.estimatedCost = costFor(supplier, recommended);
        suggestion.supplierName = supplier.supplierName;
        suggestion.reason = reason(item, supplier, dynamicSafetyStock, recommended);
        suggestion.status = "PENDING";
        suggestion.createdAt = now();
        return suggestion;
    }

    private BigDecimal dynamicSafetyStock(InventoryItem item, SupplierProfile supplier) {
        List<InventoryConsumption> recent = consumptions.findBySkuAndConsumedAtAfter(item.sku, LocalDateTime.now().minusDays(90));
        BigDecimal fallback = item.safetyStock == null ? BigDecimal.ZERO : item.safetyStock;
        if (recent.isEmpty()) {
            return fallback.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal total = recent.stream()
                .map(entry -> entry.quantity == null ? BigDecimal.ZERO : entry.quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal daily = total.divide(new BigDecimal("90"), 4, RoundingMode.HALF_UP);
        BigDecimal leadAndBuffer = BigDecimal.valueOf(supplier.leadTimeDays == null ? 3 : supplier.leadTimeDays).add(BUFFER_DAYS);
        return daily.multiply(leadAndBuffer).max(fallback).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal recommendedQuantity(InventoryItem item, SupplierProfile supplier, BigDecimal dynamicSafetyStock) {
        BigDecimal quantity = item.quantity == null ? BigDecimal.ZERO : item.quantity;
        BigDecimal shortage = dynamicSafetyStock.subtract(quantity);
        if (shortage.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal minOrder = supplier.minOrderQuantity == null ? BigDecimal.ONE : supplier.minOrderQuantity;
        BigDecimal recommended = shortage.max(minOrder);
        return recommended.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal costFor(SupplierProfile supplier, BigDecimal quantity) {
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal multiplier = discountMultiplier(supplier.discountBreaks, quantity);
        return quantity.multiply(supplier.unitPrice).multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal discountMultiplier(String breaks, BigDecimal quantity) {
        BigDecimal multiplier = BigDecimal.ONE;
        for (String part : text(breaks, "").split(",")) {
            if (!part.contains(":")) {
                continue;
            }
            String[] pair = part.split(":");
            if (quantity.compareTo(new BigDecimal(pair[0])) >= 0) {
                multiplier = new BigDecimal(pair[1]);
            }
        }
        return multiplier;
    }

    private SupplierProfile supplierFor(String sku) {
        return suppliers.findFirstBySkuAndActiveTrueOrderByLeadTimeDaysAsc(sku).orElseGet(() -> {
            SupplierProfile supplier = new SupplierProfile();
            supplier.sku = sku;
            supplier.supplierName = "默认供应商";
            supplier.leadTimeDays = 3;
            supplier.minOrderQuantity = BigDecimal.ONE;
            supplier.unitPrice = BigDecimal.ONE;
            supplier.discountBreaks = "1:1.00";
            supplier.active = true;
            return supplier;
        });
    }

    private String reason(InventoryItem item, SupplierProfile supplier, BigDecimal dynamicSafetyStock, BigDecimal recommended) {
        return "近90天消耗计算动态安全库存 " + dynamicSafetyStock.toPlainString()
                + "，供应商提前期 " + supplier.leadTimeDays + " 天，建议补货 "
                + recommended.toPlainString() + text(item.unit, "件") + "。";
    }

    private UserAccount requireOps(String username, boolean mutate) {
        UserAccount user = identityService.requireUser(username);
        if (mutate && !"ADMIN".equals(user.role) && !"OPS".equals(user.role)) {
            throw forbidden("只有总部运营或系统管理员可以生成/审批采购建议。");
        }
        if (!List.of("OPS", "ADMIN", "MANAGER").contains(user.role)) {
            throw forbidden("当前角色不能查看智能补货。");
        }
        return user;
    }

    private void ensureSupplier(String sku, String name, int leadDays, String minOrder, String unitPrice, String breaks) {
        if (suppliers.findFirstBySkuAndActiveTrueOrderByLeadTimeDaysAsc(sku).isPresent()) {
            return;
        }
        SupplierProfile supplier = new SupplierProfile();
        supplier.sku = sku;
        supplier.supplierName = name;
        supplier.leadTimeDays = leadDays;
        supplier.minOrderQuantity = new BigDecimal(minOrder);
        supplier.unitPrice = new BigDecimal(unitPrice);
        supplier.discountBreaks = breaks;
        supplier.active = true;
        suppliers.save(supplier);
    }
}
