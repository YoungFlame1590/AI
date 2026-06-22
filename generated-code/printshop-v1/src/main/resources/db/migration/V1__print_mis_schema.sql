CREATE TABLE stores (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  address VARCHAR(255),
  phone VARCHAR(50),
  active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE user_accounts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(64) NOT NULL UNIQUE,
  password VARCHAR(128) NOT NULL,
  role VARCHAR(32) NOT NULL,
  display_name VARCHAR(100) NOT NULL,
  store_id BIGINT,
  active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE print_orders (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no VARCHAR(40) NOT NULL UNIQUE,
  customer_id BIGINT,
  customer_name VARCHAR(100) NOT NULL,
  store_id BIGINT,
  store_name VARCHAR(100),
  product_type VARCHAR(80) NOT NULL,
  color_mode VARCHAR(40),
  page_count INT,
  copies INT,
  due_at VARCHAR(40),
  delivery_mode VARCHAR(40),
  priority VARCHAR(20),
  status VARCHAR(40) NOT NULL,
  payment_status VARCHAR(40),
  current_step VARCHAR(255),
  total_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  paid_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE order_files (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  file_path VARCHAR(500) NOT NULL,
  size_bytes BIGINT NOT NULL DEFAULT 0,
  file_status VARCHAR(40) NOT NULL,
  uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE quotations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  quote_no VARCHAR(40) NOT NULL UNIQUE,
  order_id BIGINT NOT NULL,
  version_no INT NOT NULL DEFAULT 1,
  subtotal DECIMAL(12,2) NOT NULL DEFAULT 0,
  discount_rate DECIMAL(8,4) NOT NULL DEFAULT 1,
  final_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  status VARCHAR(40) NOT NULL,
  approved_by VARCHAR(100),
  valid_until VARCHAR(40),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE job_tickets (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  ticket_no VARCHAR(40) NOT NULL UNIQUE,
  order_id BIGINT NOT NULL,
  quotation_id BIGINT,
  specs VARCHAR(500),
  paper_type VARCHAR(80),
  binding VARCHAR(80),
  status VARCHAR(40) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE production_tasks (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  task_no VARCHAR(40) NOT NULL UNIQUE,
  job_ticket_id BIGINT NOT NULL,
  station VARCHAR(80) NOT NULL,
  operator_name VARCHAR(100),
  planned_start VARCHAR(40),
  planned_end VARCHAR(40),
  status VARCHAR(40) NOT NULL,
  progress_percent INT NOT NULL DEFAULT 0,
  quality_status VARCHAR(40)
);

CREATE TABLE inventory_items (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  sku VARCHAR(40) NOT NULL UNIQUE,
  item_name VARCHAR(100) NOT NULL,
  category VARCHAR(60),
  unit VARCHAR(20),
  quantity DECIMAL(12,2) NOT NULL DEFAULT 0,
  safety_stock DECIMAL(12,2) NOT NULL DEFAULT 0,
  location VARCHAR(100)
);

CREATE TABLE delivery_tasks (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  task_no VARCHAR(40) NOT NULL UNIQUE,
  order_id BIGINT NOT NULL,
  mode VARCHAR(40) NOT NULL,
  carrier_name VARCHAR(100),
  target_store VARCHAR(100),
  status VARCHAR(40) NOT NULL,
  signed_by VARCHAR(100),
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE invoices (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  invoice_no VARCHAR(40) NOT NULL UNIQUE,
  order_id BIGINT NOT NULL,
  title VARCHAR(160),
  tax_no VARCHAR(80),
  amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  status VARCHAR(40) NOT NULL,
  issued_at VARCHAR(40)
);

CREATE TABLE payments (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  payment_no VARCHAR(40) NOT NULL UNIQUE,
  order_id BIGINT NOT NULL,
  amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  method VARCHAR(40),
  status VARCHAR(40) NOT NULL,
  paid_at VARCHAR(40)
);

CREATE TABLE audit_logs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  operator VARCHAR(100) NOT NULL,
  role VARCHAR(32) NOT NULL,
  action VARCHAR(80) NOT NULL,
  target_type VARCHAR(80),
  target_id VARCHAR(80),
  detail VARCHAR(1000),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO stores(code, name, address, phone) VALUES
('STORE-A', '大学城店', '大学城商业街 18 号', '020-1000001'),
('STORE-B', '市中心店', '中心路 66 号', '020-1000002'),
('STORE-C', '西区店', '西区创意园 9 号', '020-1000003');

INSERT INTO user_accounts(username, password, role, display_name, store_id) VALUES
('customer', 'demo123', 'CUSTOMER', '张同学', 1),
('clerk', 'demo123', 'CLERK', '前台小周', 1),
('manager', 'demo123', 'MANAGER', '店长林', 1),
('ops', 'demo123', 'OPS', '运营许', NULL),
('finance', 'demo123', 'FINANCE', '财务陈', NULL),
('courier', 'demo123', 'COURIER', '配送赵', NULL),
('admin', 'demo123', 'ADMIN', '系统管理员', NULL);

INSERT INTO print_orders(order_no, customer_id, customer_name, store_id, store_name, product_type, color_mode, page_count, copies, due_at, delivery_mode, priority, status, payment_status, current_step, total_amount, paid_amount) VALUES
('ORD-20260618-001', 1, '张同学', 1, '大学城店', '论文胶装', '黑白+彩页', 80, 2, '今日 18:00', '到店自提', '普通', 'SUBMITTED', 'UNPAID', '等待店员审核文件', 128.00, 0),
('ORD-20260618-002', 1, '行政王老师', 1, '大学城店', '批量培训手册', '彩色', 120, 40, '明日 12:00', '跨店配送', '加急', 'QUOTED', 'PARTIAL', '折扣待店长审批', 980.00, 200.00),
('ORD-20260618-003', 1, '商户李先生', 2, '市中心店', '海报写真', '彩色', 2, 20, '今日 21:00', '外协配送', '普通', 'IN_PRODUCTION', 'PAID', '生产中，等待质检', 420.00, 420.00);

INSERT INTO order_files(order_id, file_name, file_path, size_bytes, file_status) VALUES
(1, 'thesis.pdf', 'uploads/thesis.pdf', 5242880, 'CHECKED'),
(2, 'training-manual.zip', 'uploads/training-manual.zip', 18874368, 'NEED_REVIEW'),
(3, 'poster.ai', 'uploads/poster.ai', 8388608, 'CHECKED');

INSERT INTO quotations(quote_no, order_id, subtotal, discount_rate, final_amount, status, approved_by, valid_until) VALUES
('QUO-20260618-001', 1, 128.00, 1.0000, 128.00, 'SENT', NULL, '2026-06-25'),
('QUO-20260618-002', 2, 1100.00, 0.8900, 980.00, 'PENDING_APPROVAL', NULL, '2026-06-25');

INSERT INTO job_tickets(ticket_no, order_id, quotation_id, specs, paper_type, binding, status) VALUES
('JOB-20260618-001', 3, NULL, 'A2 海报写真 20 张，覆膜', '写真纸', '无', 'READY');

INSERT INTO production_tasks(task_no, job_ticket_id, station, operator_name, planned_start, planned_end, status, progress_percent, quality_status) VALUES
('PRO-20260618-001', 1, '写真机-01', '生产吴', '2026-06-18 14:00', '2026-06-18 17:30', 'RUNNING', 60, 'PENDING');

INSERT INTO inventory_items(sku, item_name, category, unit, quantity, safety_stock, location) VALUES
('PAPER-A4-80G', 'A4 80g 复印纸', '纸张', '包', 42, 20, '大学城店-仓库A'),
('INK-CYAN', '青色墨水', '耗材', '瓶', 6, 5, '市中心店-写真区'),
('COVER-GLUE', '胶装封面', '装订', '张', 180, 100, '大学城店-装订区');

INSERT INTO delivery_tasks(task_no, order_id, mode, carrier_name, target_store, status, signed_by) VALUES
('DLV-20260618-001', 3, '外协配送', '配送赵', '客户地址', 'ASSIGNED', NULL);

INSERT INTO invoices(invoice_no, order_id, title, tax_no, amount, status, issued_at) VALUES
('INV-20260618-001', 3, '商户李先生', '91440000TEST', 420.00, 'WAITING', NULL);

INSERT INTO payments(payment_no, order_id, amount, method, status, paid_at) VALUES
('PAY-20260618-001', 2, 200.00, '微信', 'SUCCESS', '2026-06-18 10:20'),
('PAY-20260618-002', 3, 420.00, '支付宝', 'SUCCESS', '2026-06-18 11:10');

INSERT INTO audit_logs(operator, role, action, target_type, target_id, detail) VALUES
('system', 'ADMIN', 'SEED_DATA', 'SYSTEM', 'printshop-v1', '初始化准生产版 Print MIS 演示数据');
