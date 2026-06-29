ALTER TABLE delivery_tasks ADD COLUMN carrier_username VARCHAR(100);

UPDATE delivery_tasks
SET carrier_username = (
  SELECT MIN(user_accounts.username)
  FROM user_accounts
  WHERE user_accounts.display_name = delivery_tasks.carrier_name
);

ALTER TABLE user_accounts DROP INDEX uk_user_accounts_display_name;
