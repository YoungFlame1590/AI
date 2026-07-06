CREATE TABLE design_templates (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  template_no VARCHAR(40) NOT NULL UNIQUE,
  title VARCHAR(120) NOT NULL,
  category VARCHAR(60),
  product_type VARCHAR(80) NOT NULL,
  color_mode VARCHAR(40),
  page_count INT,
  default_copies INT,
  size_name VARCHAR(60),
  price_type VARCHAR(40),
  published BOOLEAN NOT NULL DEFAULT TRUE,
  canvas_json VARCHAR(5000),
  created_by VARCHAR(100),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE design_projects (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_no VARCHAR(40) NOT NULL UNIQUE,
  template_id BIGINT NOT NULL,
  customer_id BIGINT,
  customer_name VARCHAR(100),
  store_id BIGINT,
  title VARCHAR(120) NOT NULL,
  status VARCHAR(40) NOT NULL,
  current_version_no INT NOT NULL DEFAULT 1,
  canvas_json VARCHAR(5000),
  submitted_order_id BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE design_project_versions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  label VARCHAR(120),
  canvas_json VARCHAR(5000),
  saved_by VARCHAR(100),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE inventory_consumptions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  sku VARCHAR(40) NOT NULL,
  item_name VARCHAR(100),
  quantity DECIMAL(12,2) NOT NULL DEFAULT 0,
  store_name VARCHAR(100),
  order_no VARCHAR(40),
  consumed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE supplier_profiles (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  sku VARCHAR(40) NOT NULL,
  supplier_name VARCHAR(120) NOT NULL,
  lead_time_days INT NOT NULL DEFAULT 3,
  min_order_quantity DECIMAL(12,2) NOT NULL DEFAULT 1,
  unit_price DECIMAL(12,2) NOT NULL DEFAULT 1,
  discount_breaks VARCHAR(1000),
  active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE purchase_suggestions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  suggestion_no VARCHAR(40) NOT NULL UNIQUE,
  sku VARCHAR(40) NOT NULL,
  item_name VARCHAR(100),
  current_quantity DECIMAL(12,2) NOT NULL DEFAULT 0,
  dynamic_safety_stock DECIMAL(12,2) NOT NULL DEFAULT 0,
  recommended_quantity DECIMAL(12,2) NOT NULL DEFAULT 0,
  estimated_cost DECIMAL(12,2) NOT NULL DEFAULT 0,
  supplier_name VARCHAR(120),
  reason VARCHAR(1000),
  status VARCHAR(40) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  approved_by VARCHAR(100),
  approved_at TIMESTAMP
);

CREATE TABLE service_review_invitations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  order_no VARCHAR(40),
  customer_id BIGINT,
  customer_name VARCHAR(100),
  store_id BIGINT,
  status VARCHAR(40) NOT NULL,
  invited_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  responded_at TIMESTAMP
);

CREATE TABLE service_reviews (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  order_no VARCHAR(40),
  customer_id BIGINT,
  customer_name VARCHAR(100),
  store_id BIGINT,
  print_quality_rating INT NOT NULL,
  timeliness_rating INT NOT NULL,
  staff_rating INT NOT NULL,
  value_rating INT NOT NULL,
  overall_rating INT NOT NULL,
  comment VARCHAR(1000),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE complaint_tickets (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  ticket_no VARCHAR(40) NOT NULL UNIQUE,
  review_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  order_no VARCHAR(40),
  customer_name VARCHAR(100),
  store_id BIGINT,
  status VARCHAR(40) NOT NULL,
  severity VARCHAR(40),
  customer_comment VARCHAR(1000),
  manager_reply VARCHAR(1000),
  replied_by VARCHAR(100),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  replied_at TIMESTAMP,
  closed_at TIMESTAMP
);

CREATE TABLE delivery_quotes (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  quote_no VARCHAR(40) NOT NULL UNIQUE,
  order_id BIGINT NOT NULL,
  channel_code VARCHAR(40) NOT NULL,
  channel_name VARCHAR(100) NOT NULL,
  pickup_address VARCHAR(255),
  delivery_address VARCHAR(255),
  package_weight_kg DECIMAL(10,2) NOT NULL DEFAULT 1,
  estimated_fee DECIMAL(12,2) NOT NULL DEFAULT 0,
  estimated_minutes INT NOT NULL DEFAULT 60,
  status VARCHAR(40) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  confirmed_at TIMESTAMP
);

CREATE TABLE delivery_tracking_events (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  delivery_task_id BIGINT NOT NULL,
  tracking_no VARCHAR(80),
  status VARCHAR(80) NOT NULL,
  location VARCHAR(120),
  message VARCHAR(500),
  occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE delivery_tasks ADD COLUMN delivery_channel VARCHAR(40);
ALTER TABLE delivery_tasks ADD COLUMN tracking_no VARCHAR(80);
ALTER TABLE delivery_tasks ADD COLUMN delivery_fee DECIMAL(12,2) NOT NULL DEFAULT 0;
ALTER TABLE delivery_tasks ADD COLUMN external_status VARCHAR(80);
ALTER TABLE delivery_tasks ADD COLUMN estimated_minutes INT;

INSERT INTO design_templates(template_no, title, category, product_type, color_mode, page_count, default_copies, size_name, price_type, published, canvas_json, created_by) VALUES
('TPL-CARD-001', '商务名片模板', '名片', '名片快印', '彩色', 1, 100, '90x54mm', 'FREE', TRUE, '{"objects":[{"type":"text","text":"公司名称","x":80,"y":45},{"type":"text","text":"姓名 / 职位","x":80,"y":90},{"type":"qr","text":"联系方式二维码","x":290,"y":55}]}', 'system'),
('TPL-FLYER-001', '活动宣传单模板', '宣传单', '宣传单页', '彩色', 1, 200, 'A5', 'FREE', TRUE, '{"objects":[{"type":"text","text":"活动标题","x":70,"y":70},{"type":"image","text":"主视觉图片","x":90,"y":130},{"type":"text","text":"优惠信息","x":80,"y":300}]}', 'system'),
('TPL-POSTER-001', '门店海报模板', '海报', '海报写真', '彩色', 1, 5, 'A2', 'PAID', TRUE, '{"objects":[{"type":"text","text":"海报标题","x":90,"y":80},{"type":"logo","text":"Logo","x":310,"y":60},{"type":"text","text":"地址与电话","x":90,"y":360}]}', 'system');

INSERT INTO supplier_profiles(sku, supplier_name, lead_time_days, min_order_quantity, unit_price, discount_breaks, active) VALUES
('PAPER-A4-80G', '总部纸张集采供应商', 3, 500, 0.05, '500:1.00,2000:0.95,5000:0.90', TRUE),
('PAPER-COATED-300G', '铜版纸区域供应商', 5, 200, 0.18, '200:1.00,1000:0.93,3000:0.88', TRUE),
('INK-COLOR', '彩色耗材供应商', 4, 50, 1.20, '50:1.00,200:0.96,500:0.91', TRUE),
('BINDING-CONSUMABLE', '装订耗材供应商', 2, 80, 0.35, '80:1.00,300:0.94,800:0.89', TRUE);
