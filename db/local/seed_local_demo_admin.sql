-- Local-only opt-in admin seed.
-- This file is intentionally outside db/init so Docker fresh init will not run it.
-- Before running manually, set @enable_local_demo_admin_seed to 1 and provide a
-- bcrypt hash generated for your local password.
SET NAMES utf8mb4;

SET @enable_local_demo_admin_seed := COALESCE(@enable_local_demo_admin_seed, 0);
SET @local_demo_admin_uid := 990000000000000001;
SET @local_demo_admin_email := 'demo.admin@offerlab.local';
SET @local_demo_admin_bcrypt_hash := COALESCE(@local_demo_admin_bcrypt_hash, '');
SET @local_demo_admin_seed_succeeded := 0;

DROP TEMPORARY TABLE IF EXISTS offerlab_local_admin_assertion;

DELIMITER $$
DROP PROCEDURE IF EXISTS offerlab_seed_local_demo_admin$$
CREATE PROCEDURE offerlab_seed_local_demo_admin()
BEGIN
    DECLARE server_major INT DEFAULT 0;
    DECLARE server_minor INT DEFAULT 0;
    DECLARE server_patch INT DEFAULT 0;

    SET server_major = CAST(SUBSTRING_INDEX(VERSION(), '.', 1) AS UNSIGNED);
    SET server_minor = CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(VERSION(), '.', 2), '.', -1) AS UNSIGNED);
    SET server_patch = CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(VERSION(), '.', 3), '.', -1) AS UNSIGNED);

    IF server_major < 8
       OR (server_major = 8 AND server_minor = 0 AND server_patch < 16) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Local admin seed requires MySQL 8.0.16 or newer for enforced CHECK constraints.';
    END IF;

    CREATE TEMPORARY TABLE offerlab_local_admin_assertion (
        failure_count INT NOT NULL,
        CONSTRAINT chk_offerlab_local_admin_seed_succeeded CHECK (failure_count = 0)
    ) ENGINE=InnoDB;

    BEGIN
        DECLARE identity_conflicts BIGINT DEFAULT 0;
        DECLARE postcondition_failures INT DEFAULT 0;
        DECLARE EXIT HANDLER FOR SQLEXCEPTION
        BEGIN
            ROLLBACK;
            SET @local_demo_admin_seed_succeeded := 0;
        END;

        IF @enable_local_demo_admin_seed <> 1
           OR @local_demo_admin_bcrypt_hash IS NULL
           OR LENGTH(TRIM(@local_demo_admin_bcrypt_hash)) <> 60
           OR LEFT(TRIM(@local_demo_admin_bcrypt_hash), 4) NOT IN ('$2a$', '$2b$', '$2y$')
           OR SUBSTRING(TRIM(@local_demo_admin_bcrypt_hash), 5, 2) NOT REGEXP '^(0[4-9]|[12][0-9]|3[01])$'
           OR SUBSTRING(TRIM(@local_demo_admin_bcrypt_hash), 7, 1) <> '$'
           OR SUBSTRING(TRIM(@local_demo_admin_bcrypt_hash), 8, 53) NOT REGEXP '^[./A-Za-z0-9]{53}$' THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Local admin seed is opt-in only; set enable flag and a valid 60-character bcrypt hash before running.';
        END IF;

        SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
        START TRANSACTION;

        SET identity_conflicts = (
            SELECT COUNT(*)
            FROM t_user_account account
            WHERE account.id = @local_demo_admin_uid
              AND NOT (
                  account.is_deleted = 0
                  AND account.email IN ('demo.author@offerlab.local', 'demo.admin@offerlab.local')
              )
        ) + (
            SELECT COUNT(*)
            FROM t_user_account account
            WHERE account.email IN ('demo.author@offerlab.local', 'demo.admin@offerlab.local')
              AND account.id <> @local_demo_admin_uid
        ) + (
            SELECT COUNT(*)
            FROM t_user_profile profile
            WHERE profile.id = @local_demo_admin_uid
              AND NOT EXISTS (
                  SELECT 1
                  FROM t_user_account account
                  WHERE account.id = profile.id
                    AND account.is_deleted = 0
                    AND account.email IN ('demo.author@offerlab.local', 'demo.admin@offerlab.local')
              )
        ) + (
            SELECT COUNT(*)
            FROM t_user_admin admin
            WHERE admin.uid = @local_demo_admin_uid
              AND NOT EXISTS (
                  SELECT 1
                  FROM t_user_account account
                  WHERE account.id = admin.uid
                    AND account.is_deleted = 0
                    AND account.email IN ('demo.author@offerlab.local', 'demo.admin@offerlab.local')
              )
        );

        IF identity_conflicts <> 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'local demo admin identity conflict; existing accounts were not modified';
        END IF;

        INSERT INTO t_user_account
            (id, email, password_hash, password_salt, account_status)
        VALUES
            (@local_demo_admin_uid, @local_demo_admin_email, TRIM(@local_demo_admin_bcrypt_hash), '', 1)
        ON DUPLICATE KEY UPDATE
            email = VALUES(email),
            password_hash = VALUES(password_hash),
            password_salt = VALUES(password_salt),
            account_status = VALUES(account_status),
            update_time = CURRENT_TIMESTAMP(3);

        INSERT INTO t_user_profile
            (id, nickname, avatar_url, bio, intent_json)
        VALUES
            (
                @local_demo_admin_uid,
                'OfferLab Local Admin',
                NULL,
                'Local-only demo administrator created by an explicit seed script.',
                JSON_OBJECT('localOnly', TRUE)
            )
        ON DUPLICATE KEY UPDATE
            nickname = VALUES(nickname),
            avatar_url = VALUES(avatar_url),
            bio = VALUES(bio),
            intent_json = VALUES(intent_json),
            is_deleted = 0,
            update_time = CURRENT_TIMESTAMP(3);

        INSERT INTO t_user_admin
            (uid, role_code, enabled, remark, operator_uid)
        VALUES
            (@local_demo_admin_uid, 'ADMIN', 1, 'local-only opt-in demo admin seed', @local_demo_admin_uid)
        ON DUPLICATE KEY UPDATE
            enabled = VALUES(enabled),
            remark = VALUES(remark),
            operator_uid = VALUES(operator_uid),
            update_time = CURRENT_TIMESTAMP(3);

        SET postcondition_failures = IF(
            (
                SELECT COUNT(*)
                FROM t_user_account account
                JOIN t_user_profile profile ON profile.id = account.id
                JOIN t_user_admin admin
                  ON admin.uid = account.id
                 AND admin.role_code = 'ADMIN'
                 AND admin.enabled = 1
                WHERE account.id = @local_demo_admin_uid
                  AND account.email = @local_demo_admin_email
                  AND account.password_hash = TRIM(@local_demo_admin_bcrypt_hash)
                  AND account.account_status = 1
                  AND account.is_deleted = 0
                  AND profile.is_deleted = 0
            ) = 1,
            0,
            1
        );

        IF postcondition_failures <> 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'local demo admin postcondition failed; transaction was rolled back';
        END IF;

        COMMIT;
        SET @local_demo_admin_seed_succeeded := 1;
    END;
END$$
CALL offerlab_seed_local_demo_admin()$$
SET @local_demo_admin_bcrypt_hash := NULL$$
SET @enable_local_demo_admin_seed := 0$$
DROP PROCEDURE IF EXISTS offerlab_seed_local_demo_admin$$
DELIMITER ;

INSERT INTO offerlab_local_admin_assertion (failure_count)
SELECT IF(
    COALESCE(@local_demo_admin_seed_succeeded, 0) = 1
        AND (
            SELECT COUNT(*)
            FROM t_user_account account
            JOIN t_user_profile profile ON profile.id = account.id
            JOIN t_user_admin admin
              ON admin.uid = account.id
             AND admin.role_code = 'ADMIN'
             AND admin.enabled = 1
            WHERE account.id = @local_demo_admin_uid
              AND account.email = @local_demo_admin_email
              AND account.account_status = 1
              AND account.is_deleted = 0
              AND profile.is_deleted = 0
        ) = 1,
    0,
    1
);
DROP TEMPORARY TABLE offerlab_local_admin_assertion;
SET @local_demo_admin_seed_succeeded := NULL;
