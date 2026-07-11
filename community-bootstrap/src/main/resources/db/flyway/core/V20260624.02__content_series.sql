SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS t_content_series (
    id           BIGINT       NOT NULL PRIMARY KEY,
    creator_uid  BIGINT       NOT NULL,
    title        VARCHAR(120) NOT NULL,
    description  VARCHAR(1000) NULL,
    domain       TINYINT      NOT NULL,
    cover_url    VARCHAR(512) NULL,
    visibility   TINYINT      NOT NULL DEFAULT 2,
    create_time  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted   TINYINT      NOT NULL DEFAULT 0,
    KEY idx_content_series_creator_update (creator_uid, update_time, id),
    KEY idx_content_series_domain_update (domain, update_time, id),
    KEY idx_content_series_public_creator (visibility, creator_uid, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User-owned content series';

CREATE TABLE IF NOT EXISTS t_content_series_post (
    id           BIGINT      NOT NULL PRIMARY KEY,
    series_id    BIGINT      NOT NULL,
    post_id      BIGINT      NOT NULL,
    sort_order   INT         NOT NULL DEFAULT 0,
    create_time  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted   TINYINT     NOT NULL DEFAULT 0,
    UNIQUE KEY uk_content_series_post (series_id, post_id),
    KEY idx_content_series_post_series_sort (series_id, sort_order, id),
    KEY idx_content_series_post_post (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Post membership in a content series';

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260624_content_series_add_column_if_missing $$
CREATE PROCEDURE v20260624_content_series_add_column_if_missing(
    IN p_table VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_sql TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND COLUMN_NAME = p_column
    ) THEN
        SET @ddl = p_sql;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DROP PROCEDURE IF EXISTS v20260624_content_series_add_index_if_missing $$
CREATE PROCEDURE v20260624_content_series_add_index_if_missing(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64),
    IN p_sql TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND INDEX_NAME = p_index
    ) THEN
        SET @ddl = p_sql;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DELIMITER ;

CALL v20260624_content_series_add_column_if_missing('t_content_series', 'id',
    'ALTER TABLE t_content_series ADD COLUMN id BIGINT NOT NULL FIRST');
CALL v20260624_content_series_add_column_if_missing('t_content_series', 'creator_uid',
    'ALTER TABLE t_content_series ADD COLUMN creator_uid BIGINT NOT NULL AFTER id');
CALL v20260624_content_series_add_column_if_missing('t_content_series', 'title',
    'ALTER TABLE t_content_series ADD COLUMN title VARCHAR(120) NOT NULL AFTER creator_uid');
CALL v20260624_content_series_add_column_if_missing('t_content_series', 'description',
    'ALTER TABLE t_content_series ADD COLUMN description VARCHAR(1000) NULL AFTER title');
CALL v20260624_content_series_add_column_if_missing('t_content_series', 'domain',
    'ALTER TABLE t_content_series ADD COLUMN domain TINYINT NOT NULL AFTER description');
CALL v20260624_content_series_add_column_if_missing('t_content_series', 'cover_url',
    'ALTER TABLE t_content_series ADD COLUMN cover_url VARCHAR(512) NULL AFTER domain');
CALL v20260624_content_series_add_column_if_missing('t_content_series', 'visibility',
    'ALTER TABLE t_content_series ADD COLUMN visibility TINYINT NOT NULL DEFAULT 2 AFTER cover_url');
CALL v20260624_content_series_add_column_if_missing('t_content_series', 'create_time',
    'ALTER TABLE t_content_series ADD COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) AFTER visibility');
CALL v20260624_content_series_add_column_if_missing('t_content_series', 'update_time',
    'ALTER TABLE t_content_series ADD COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) AFTER create_time');
CALL v20260624_content_series_add_column_if_missing('t_content_series', 'is_deleted',
    'ALTER TABLE t_content_series ADD COLUMN is_deleted TINYINT NOT NULL DEFAULT 0 AFTER update_time');

CALL v20260624_content_series_add_column_if_missing('t_content_series_post', 'id',
    'ALTER TABLE t_content_series_post ADD COLUMN id BIGINT NOT NULL FIRST');
CALL v20260624_content_series_add_column_if_missing('t_content_series_post', 'series_id',
    'ALTER TABLE t_content_series_post ADD COLUMN series_id BIGINT NOT NULL AFTER id');
CALL v20260624_content_series_add_column_if_missing('t_content_series_post', 'post_id',
    'ALTER TABLE t_content_series_post ADD COLUMN post_id BIGINT NOT NULL AFTER series_id');
CALL v20260624_content_series_add_column_if_missing('t_content_series_post', 'sort_order',
    'ALTER TABLE t_content_series_post ADD COLUMN sort_order INT NOT NULL DEFAULT 0 AFTER post_id');
CALL v20260624_content_series_add_column_if_missing('t_content_series_post', 'create_time',
    'ALTER TABLE t_content_series_post ADD COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) AFTER sort_order');
CALL v20260624_content_series_add_column_if_missing('t_content_series_post', 'update_time',
    'ALTER TABLE t_content_series_post ADD COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) AFTER create_time');
CALL v20260624_content_series_add_column_if_missing('t_content_series_post', 'is_deleted',
    'ALTER TABLE t_content_series_post ADD COLUMN is_deleted TINYINT NOT NULL DEFAULT 0 AFTER update_time');

CALL v20260624_content_series_add_index_if_missing('t_content_series', 'idx_content_series_creator_update',
    'ALTER TABLE t_content_series ADD KEY idx_content_series_creator_update (creator_uid, update_time, id)');
CALL v20260624_content_series_add_index_if_missing('t_content_series', 'idx_content_series_domain_update',
    'ALTER TABLE t_content_series ADD KEY idx_content_series_domain_update (domain, update_time, id)');
CALL v20260624_content_series_add_index_if_missing('t_content_series', 'idx_content_series_public_creator',
    'ALTER TABLE t_content_series ADD KEY idx_content_series_public_creator (visibility, creator_uid, id)');
CALL v20260624_content_series_add_index_if_missing('t_content_series_post', 'uk_content_series_post',
    'ALTER TABLE t_content_series_post ADD UNIQUE KEY uk_content_series_post (series_id, post_id)');
CALL v20260624_content_series_add_index_if_missing('t_content_series_post', 'idx_content_series_post_series_sort',
    'ALTER TABLE t_content_series_post ADD KEY idx_content_series_post_series_sort (series_id, sort_order, id)');
CALL v20260624_content_series_add_index_if_missing('t_content_series_post', 'idx_content_series_post_post',
    'ALTER TABLE t_content_series_post ADD KEY idx_content_series_post_post (post_id)');

DROP PROCEDURE IF EXISTS v20260624_content_series_add_column_if_missing;
DROP PROCEDURE IF EXISTS v20260624_content_series_add_index_if_missing;
