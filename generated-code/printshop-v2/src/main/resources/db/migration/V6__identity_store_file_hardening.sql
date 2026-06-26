UPDATE user_accounts
SET password = '$2a$10$Fs5zBvz.An9O/A.lHtW/mOPoc.WV5Nu5JVfL95U5eMHijq5glHMpu'
WHERE password = 'demo123';

ALTER TABLE order_files ADD COLUMN content_type VARCHAR(120);
ALTER TABLE order_files ADD COLUMN storage_name VARCHAR(255);
ALTER TABLE order_files ADD COLUMN version_no INT NOT NULL DEFAULT 1;
ALTER TABLE order_files ADD COLUMN uploaded_by VARCHAR(100);
ALTER TABLE order_files ADD COLUMN uploaded_role VARCHAR(32);
ALTER TABLE order_files ADD COLUMN remark VARCHAR(500);
ALTER TABLE order_files ADD COLUMN review_status VARCHAR(40) NOT NULL DEFAULT 'PENDING';

UPDATE order_files
SET storage_name = file_path
WHERE storage_name IS NULL;
