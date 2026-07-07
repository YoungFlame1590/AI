CREATE TABLE customer_callback_contacts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id BIGINT NOT NULL,
  customer_name VARCHAR(100),
  store_id BIGINT,
  contacted_by VARCHAR(100),
  contacted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  note VARCHAR(1000)
);

CREATE INDEX idx_callback_contacts_customer_store_time
  ON customer_callback_contacts(customer_id, store_id, contacted_at);
