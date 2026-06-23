UPDATE print_orders
SET color_mode = '黑白加彩页'
WHERE order_no = 'ORD-20260618-001';

UPDATE print_orders
SET product_type = '培训手册',
    customer_name = '张同学'
WHERE order_no = 'ORD-20260618-002';

UPDATE print_orders
SET customer_name = '张同学'
WHERE order_no = 'ORD-20260618-003';
