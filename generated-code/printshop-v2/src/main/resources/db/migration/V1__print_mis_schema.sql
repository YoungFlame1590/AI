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
