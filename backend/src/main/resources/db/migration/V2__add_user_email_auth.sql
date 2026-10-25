
-- CampusTrade V2
-- 为现有用户表增加邮箱认证所需字段
-- 适用于已经包含 users 表的数据库

ALTER TABLE users
    MODIFY COLUMN school_id VARCHAR(32) NULL;

ALTER TABLE users
    ADD COLUMN email VARCHAR(254) NULL,
    ADD COLUMN password_hash VARCHAR(255) NULL,
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE users
    ADD CONSTRAINT uk_users_email UNIQUE (email);
