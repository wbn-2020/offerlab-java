SET NAMES utf8mb4;
USE offerlab;

CREATE TABLE IF NOT EXISTS t_int_favorite_folder (
    id              BIGINT       NOT NULL PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    name            VARCHAR(80)  NOT NULL,
    description     VARCHAR(255) NULL,
    visibility      TINYINT      NOT NULL DEFAULT 2 COMMENT '1 public 2 private',
    sort_order      INT          NOT NULL DEFAULT 0,
    post_count      INT          NOT NULL DEFAULT 0,
    is_default      TINYINT      NOT NULL DEFAULT 0,
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    default_active_key TINYINT GENERATED ALWAYS AS (
        CASE WHEN is_default = 1 AND is_deleted = 0 THEN 1 ELSE NULL END
    ) STORED,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_favorite_folder_active_default (user_id, default_active_key),
    KEY idx_favorite_folder_user_sort (user_id, is_deleted, sort_order, id),
    KEY idx_favorite_folder_user_default (user_id, is_default, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='favorite folders';

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260707_favorite_add_column_if_missing $$
CREATE PROCEDURE v20260707_favorite_add_column_if_missing(
    IN p_table VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_sql TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
    )
    AND NOT EXISTS (
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

DROP PROCEDURE IF EXISTS v20260707_favorite_add_index_if_missing $$
CREATE PROCEDURE v20260707_favorite_add_index_if_missing(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64),
    IN p_sql TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
    )
    AND NOT EXISTS (
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

CALL v20260707_favorite_add_column_if_missing('t_int_favorite', 'folder_id',
    'ALTER TABLE t_int_favorite ADD COLUMN folder_id BIGINT NULL DEFAULT 0 AFTER post_id');
CALL v20260707_favorite_add_column_if_missing('t_int_favorite', 'sort_order',
    'ALTER TABLE t_int_favorite ADD COLUMN sort_order INT NOT NULL DEFAULT 0 AFTER folder_id');
CALL v20260707_favorite_add_column_if_missing('t_int_favorite', 'update_time',
    'ALTER TABLE t_int_favorite ADD COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) AFTER create_time');

CALL v20260707_favorite_add_index_if_missing('t_int_favorite', 'uk_user_post',
    'ALTER TABLE t_int_favorite ADD UNIQUE KEY uk_user_post (user_id, post_id)');
CALL v20260707_favorite_add_index_if_missing('t_int_favorite', 'idx_user_folder_sort',
    'ALTER TABLE t_int_favorite ADD KEY idx_user_folder_sort (user_id, folder_id, sort_order, create_time)');
CALL v20260707_favorite_add_index_if_missing('t_int_favorite', 'idx_folder_time',
    'ALTER TABLE t_int_favorite ADD KEY idx_folder_time (folder_id, is_deleted, create_time)');
CALL v20260707_favorite_add_index_if_missing('t_int_favorite_folder', 'idx_favorite_folder_user_sort',
    'ALTER TABLE t_int_favorite_folder ADD KEY idx_favorite_folder_user_sort (user_id, is_deleted, sort_order, id)');
CALL v20260707_favorite_add_index_if_missing('t_int_favorite_folder', 'idx_favorite_folder_user_default',
    'ALTER TABLE t_int_favorite_folder ADD KEY idx_favorite_folder_user_default (user_id, is_default, is_deleted)');

UPDATE t_int_favorite f
JOIN t_int_favorite_folder dup
  ON dup.id = f.folder_id
 AND dup.is_default = 1
 AND dup.is_deleted = 0
JOIN (
    SELECT user_id, MIN(id) AS keep_id
    FROM t_int_favorite_folder
    WHERE is_default = 1
      AND is_deleted = 0
    GROUP BY user_id
    HAVING COUNT(*) > 1
) keep_default
  ON keep_default.user_id = dup.user_id
 AND keep_default.keep_id <> dup.id
SET f.folder_id = keep_default.keep_id,
    f.update_time = CURRENT_TIMESTAMP(3)
WHERE f.is_deleted = 0;

UPDATE t_int_favorite_folder dup
JOIN (
    SELECT user_id, MIN(id) AS keep_id
    FROM t_int_favorite_folder
    WHERE is_default = 1
      AND is_deleted = 0
    GROUP BY user_id
    HAVING COUNT(*) > 1
) keep_default
  ON keep_default.user_id = dup.user_id
 AND keep_default.keep_id <> dup.id
SET dup.is_deleted = 1,
    dup.update_time = CURRENT_TIMESTAMP(3)
WHERE dup.is_default = 1
  AND dup.is_deleted = 0;

CALL v20260707_favorite_add_column_if_missing('t_int_favorite_folder', 'default_active_key',
    'ALTER TABLE t_int_favorite_folder ADD COLUMN default_active_key TINYINT GENERATED ALWAYS AS (CASE WHEN is_default = 1 AND is_deleted = 0 THEN 1 ELSE NULL END) STORED AFTER is_deleted');
CALL v20260707_favorite_add_index_if_missing('t_int_favorite_folder', 'uk_favorite_folder_active_default',
    'ALTER TABLE t_int_favorite_folder ADD UNIQUE KEY uk_favorite_folder_active_default (user_id, default_active_key)');

INSERT INTO t_int_favorite_folder (
    id, user_id, name, description, visibility, sort_order, post_count, is_default, create_time, update_time, is_deleted
)
SELECT
    -f.user_id,
    f.user_id,
    'Default Folder',
    NULL,
    2,
    0,
    SUM(CASE WHEN f.is_deleted = 0 THEN 1 ELSE 0 END),
    1,
    MIN(f.create_time),
    CURRENT_TIMESTAMP(3),
    0
FROM t_int_favorite f
WHERE f.user_id > 0
  AND (f.folder_id IS NULL OR f.folder_id = 0)
  AND NOT EXISTS (
      SELECT 1
      FROM t_int_favorite_folder ff
      WHERE ff.user_id = f.user_id
        AND ff.is_default = 1
        AND ff.is_deleted = 0
  )
GROUP BY f.user_id;

UPDATE t_int_favorite f
JOIN t_int_favorite_folder ff
  ON ff.user_id = f.user_id
 AND ff.is_default = 1
 AND ff.is_deleted = 0
SET f.folder_id = ff.id,
    f.update_time = CURRENT_TIMESTAMP(3)
WHERE f.folder_id IS NULL
   OR f.folder_id = 0;

UPDATE t_int_favorite_folder ff
LEFT JOIN (
    SELECT folder_id, COUNT(*) AS active_count
    FROM t_int_favorite
    WHERE is_deleted = 0
      AND folder_id IS NOT NULL
      AND folder_id <> 0
    GROUP BY folder_id
) c ON c.folder_id = ff.id
SET ff.post_count = COALESCE(c.active_count, 0),
    ff.update_time = CURRENT_TIMESTAMP(3)
WHERE ff.is_deleted = 0;

DROP PROCEDURE IF EXISTS v20260707_favorite_add_column_if_missing;
DROP PROCEDURE IF EXISTS v20260707_favorite_add_index_if_missing;
