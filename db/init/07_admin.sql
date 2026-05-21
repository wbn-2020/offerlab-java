-- 07_admin.sql
-- Admin roles for operations endpoints.
SET NAMES utf8mb4;
USE offerlab;

CREATE TABLE IF NOT EXISTS t_user_admin (
    uid         BIGINT      NOT NULL,
    role_code   VARCHAR(32) NOT NULL DEFAULT 'ADMIN',
    enabled     TINYINT     NOT NULL DEFAULT 1,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (uid, role_code),
    KEY idx_role_enabled (role_code, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Admin user roles';
