package com.printshop.mis.inventory;

import static com.printshop.mis.shared.MisSupport.code;
import static com.printshop.mis.shared.MisSupport.notFound;
import static com.printshop.mis.shared.MisSupport.text;

import com.printshop.mis.audit.AuditTrailService;
import com.printshop.common.exception.BusinessException;
import com.printshop.mis.domain.InventoryItem;
import com.printshop.mis.repository.InventoryItemRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InventoryService {

    private final InventoryItemRepository inventoryItems;
    private final AuditTrailService audit;

    public InventoryService(InventoryItemRepository inventoryItems, AuditTrailService audit) {
        this.inventoryItems = inventoryItems;
        this.audit = audit;
    }

    public InventoryItem saveInventory(String username, InventoryItem request) {
        InventoryItem item = request.id == null ? new InventoryItem() : getInventoryItem(request.id);
        item.sku = text(request.sku, item.sku == null ? code("SKU") : item.sku);
        item.itemName = text(request.itemName, "未命名物料");
        item.category = text(request.category, item.category);
        item.unit = text(request.unit, "件");
        item.quantity = request.quantity == null ? item.quantity : request.quantity;
        item.safetyStock = request.safetyStock == null ? item.safetyStock : request.safetyStock;
        item.location = text(request.location, item.location);
        audit.record(username, "INV", "SAVE_INVENTORY", "INVENTORY", item.id, item.sku);
        return inventoryItems.save(item);
    }

    @Transactional(readOnly = true)
    public List<InventoryItem> inventoryItems() {
        return inventoryItems.findAll();
    }

    @Transactional(readOnly = true)
    public InventoryItem getInventoryItem(Long id) {
        return inventoryItems.findById(id).orElseThrow(() -> notFound("库存物料", id));
    }

    public InventoryItem adjustInventory(String username, Long id, Map<String, Object> payload) {
        InventoryItem item = getInventoryItem(id);
        item.quantity = item.quantity.add(new BigDecimal(String.valueOf(payload.getOrDefault("delta", "0"))));
        audit.record(username, "INV", "ADJUST_INVENTORY", "INVENTORY", id, item.quantity.toPlainString());
        return inventoryItems.save(item);
    }

    public InventoryItem deleteInventory(String username, Long id) {
        InventoryItem item = getInventoryItem(id);
        inventoryItems.delete(item);
        audit.record(username, "INV", "DELETE_INVENTORY", "INVENTORY", id, item.sku);
        return item;
    }

    public void ensureDefaultInventory() {
        ensureItem("PAPER-A4-80G", "A4 80g 复印纸", "纸张", "张", new BigDecimal("5000"), new BigDecimal("500"), "大学城店");
        ensureItem("PAPER-COATED-300G", "300g 铜版纸", "纸张", "张", new BigDecimal("2000"), new BigDecimal("200"), "大学城店");
        ensureItem("INK-COLOR", "彩色墨粉/墨水", "耗材", "份", new BigDecimal("1200"), new BigDecimal("120"), "大学城店");
        ensureItem("BINDING-CONSUMABLE", "装订耗材", "耗材", "套", new BigDecimal("800"), new BigDecimal("80"), "大学城店");
    }

    public void consumeForProduction(String username, String productType, String colorMode, Integer pageCount, Integer copies) {
        ensureDefaultInventory();
        Map<String, BigDecimal> required = requiredMaterials(productType, colorMode, pageCount, copies);
        assertEnough(required);
        required.forEach((sku, quantity) -> consume(username, sku, quantity));
    }

    public void assertAvailableForProduction(String productType, String colorMode, Integer pageCount, Integer copies) {
        ensureDefaultInventory();
        assertEnough(requiredMaterials(productType, colorMode, pageCount, copies));
    }

    private Map<String, BigDecimal> requiredMaterials(String productType, String colorMode, Integer pageCount, Integer copies) {
        int safePages = Math.max(1, pageCount == null ? 1 : pageCount);
        int safeCopies = Math.max(1, copies == null ? 1 : copies);
        Map<String, BigDecimal> required = new java.util.LinkedHashMap<>();
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

    private void assertEnough(Map<String, BigDecimal> required) {
        for (Map.Entry<String, BigDecimal> entry : required.entrySet()) {
            InventoryItem item = inventoryItems.findBySku(entry.getKey()).orElseThrow(() -> notFound("库存物料", -1L));
            if (item.quantity == null || item.quantity.compareTo(entry.getValue()) < 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "库存不足，无法排产：" + item.itemName + " 需要 " + entry.getValue().toPlainString()
                                + item.unit + "，当前仅 " + (item.quantity == null ? "0" : item.quantity.toPlainString()) + item.unit + "。");
            }
        }
    }

    private void ensureItem(String sku, String itemName, String category, String unit, BigDecimal quantity, BigDecimal safetyStock, String location) {
        inventoryItems.findBySku(sku).orElseGet(() -> {
            InventoryItem item = new InventoryItem();
            item.sku = sku;
            item.itemName = itemName;
            item.category = category;
            item.unit = unit;
            item.quantity = quantity;
            item.safetyStock = safetyStock;
            item.location = location;
            return inventoryItems.save(item);
        });
    }

    private void consume(String username, String sku, BigDecimal quantity) {
        InventoryItem item = inventoryItems.findBySku(sku).orElseThrow(() -> notFound("库存物料", -1L));
        if (item.quantity == null || item.quantity.compareTo(quantity) < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "库存不足，不能扣减：" + item.itemName);
        }
        item.quantity = item.quantity.subtract(quantity);
        audit.record(username, "INV", "CONSUME_INVENTORY", "INVENTORY", item.id, sku + " -" + quantity.toPlainString());
        inventoryItems.save(item);
    }
}
