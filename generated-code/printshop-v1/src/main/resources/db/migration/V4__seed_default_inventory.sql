INSERT INTO inventory_items(sku, item_name, category, unit, quantity, safety_stock, location)
SELECT 'PAPER-A4-80G', 'A4 80g 复印纸', '纸张', '张', 5000, 500, '大学城店'
WHERE NOT EXISTS (SELECT 1 FROM inventory_items WHERE sku = 'PAPER-A4-80G');

INSERT INTO inventory_items(sku, item_name, category, unit, quantity, safety_stock, location)
SELECT 'PAPER-COATED-300G', '300g 铜版纸', '纸张', '张', 2000, 200, '大学城店'
WHERE NOT EXISTS (SELECT 1 FROM inventory_items WHERE sku = 'PAPER-COATED-300G');

INSERT INTO inventory_items(sku, item_name, category, unit, quantity, safety_stock, location)
SELECT 'INK-COLOR', '彩色墨粉/墨水', '耗材', '份', 1200, 120, '大学城店'
WHERE NOT EXISTS (SELECT 1 FROM inventory_items WHERE sku = 'INK-COLOR');

INSERT INTO inventory_items(sku, item_name, category, unit, quantity, safety_stock, location)
SELECT 'BINDING-CONSUMABLE', '装订耗材', '耗材', '套', 800, 80, '大学城店'
WHERE NOT EXISTS (SELECT 1 FROM inventory_items WHERE sku = 'BINDING-CONSUMABLE');
