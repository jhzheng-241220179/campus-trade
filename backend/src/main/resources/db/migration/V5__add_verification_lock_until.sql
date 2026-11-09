
-- CampusTrade V5
-- 为邮箱验证码增加账户级临时锁定截止时间

ALTER TABLE users
ADD COLUMN verification_locked_until DATETIME NULL;
