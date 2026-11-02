ALTER TABLE users
ADD COLUMN verification_failure_count INT NOT NULL DEFAULT 0;

ALTER TABLE users
ADD CONSTRAINT chk_users_verification_failure_count
CHECK (verification_failure_count >= 0);