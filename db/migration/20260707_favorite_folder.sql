SET NAMES utf8mb4;

-- Shared DB precheck / dry-run before execution:
-- SELECT user_id, COUNT(*) AS active_default_count
-- FROM t_int_favorite_folder
-- WHERE is_default = 1 AND is_deleted = 0
-- GROUP BY user_id
-- HAVING COUNT(*) > 1;
-- SELECT COUNT(*) AS favorites_without_folder
-- FROM t_int_favorite
-- WHERE is_deleted = 0 AND (folder_id IS NULL OR folder_id = 0);
-- SELECT user_id, post_id, COUNT(*) AS duplicate_count
-- FROM t_int_favorite
-- GROUP BY user_id, post_id
-- HAVING COUNT(*) > 1;

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
    name_active_key TINYINT GENERATED ALWAYS AS (
        CASE WHEN is_deleted = 0 THEN 1 ELSE NULL END
    ) STORED,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_favorite_folder_active_default (user_id, default_active_key),
    UNIQUE KEY uk_favorite_folder_active_name (user_id, name, name_active_key),
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

DROP PROCEDURE IF EXISTS v20260707_favorite_precheck $$
CREATE PROCEDURE v20260707_favorite_precheck()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM (
            SELECT user_id, post_id
            FROM t_int_favorite
            GROUP BY user_id, post_id
            HAVING COUNT(*) > 1
            LIMIT 1
        ) duplicates
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'duplicate t_int_favorite(user_id, post_id) rows must be merged before adding uk_user_post';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM t_int_favorite f
        JOIN t_int_favorite_folder existing
          ON existing.id = f.user_id
         AND NOT (
             existing.user_id = f.user_id
             AND existing.is_default = 1
             AND existing.is_deleted = 0
         )
        WHERE f.user_id > 0
          AND (f.folder_id IS NULL OR f.folder_id = 0)
        LIMIT 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'favorite default-folder backfill id collision; repair t_int_favorite_folder ids before migration';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM (
            SELECT user_id, name
            FROM t_int_favorite_folder
            WHERE is_deleted = 0
            GROUP BY user_id, name
            HAVING COUNT(*) > 1
            LIMIT 1
        ) duplicate_names
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'duplicate active favorite folder names must be renamed before adding uk_favorite_folder_active_name';
    END IF;
END $$

DELIMITER ;

CALL v20260707_favorite_add_column_if_missing('t_int_favorite', 'folder_id',
    'ALTER TABLE t_int_favorite ADD COLUMN folder_id BIGINT NULL DEFAULT 0 AFTER post_id');
CALL v20260707_favorite_add_column_if_missing('t_int_favorite', 'sort_order',
    'ALTER TABLE t_int_favorite ADD COLUMN sort_order INT NOT NULL DEFAULT 0 AFTER folder_id');
CALL v20260707_favorite_add_column_if_missing('t_int_favorite', 'update_time',
    'ALTER TABLE t_int_favorite ADD COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) AFTER create_time');

CALL v20260707_favorite_precheck();

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

-- Data repair: merge duplicate active default folders by moving favorites to the
-- lowest folder id, then soft-delete the duplicate default folders.
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

-- If a user already created an active non-default folder named like the
-- backfilled default folder, rename it before adding the active-name unique
-- guard. Otherwise the generated unique key can fail, or the default backfill
-- can collide with user data.
UPDATE t_int_favorite_folder ff
LEFT JOIN t_int_favorite_folder existing_default
  ON existing_default.user_id = ff.user_id
 AND existing_default.is_default = 1
 AND existing_default.is_deleted = 0
SET ff.name = CONCAT(ff.name, ' #', ff.id),
    ff.update_time = CURRENT_TIMESTAMP(3)
WHERE ff.is_default = 0
  AND ff.is_deleted = 0
  AND LOWER(ff.name) = LOWER('Default Folder')
  AND existing_default.id IS NULL
  AND EXISTS (
      SELECT 1
      FROM t_int_favorite f
      WHERE f.user_id = ff.user_id
        AND f.is_deleted = 0
        AND (f.folder_id IS NULL OR f.folder_id = 0)
  );

-- Structure migration: add the generated active-default guard only after the
-- duplicate default-folder repair above, so the unique key can be created safely.
CALL v20260707_favorite_add_column_if_missing('t_int_favorite_folder', 'default_active_key',
    'ALTER TABLE t_int_favorite_folder ADD COLUMN default_active_key TINYINT GENERATED ALWAYS AS (CASE WHEN is_default = 1 AND is_deleted = 0 THEN 1 ELSE NULL END) STORED AFTER is_deleted');
CALL v20260707_favorite_add_column_if_missing('t_int_favorite_folder', 'name_active_key',
    'ALTER TABLE t_int_favorite_folder ADD COLUMN name_active_key TINYINT GENERATED ALWAYS AS (CASE WHEN is_deleted = 0 THEN 1 ELSE NULL END) STORED AFTER default_active_key');
CALL v20260707_favorite_add_index_if_missing('t_int_favorite_folder', 'uk_favorite_folder_active_default',
    'ALTER TABLE t_int_favorite_folder ADD UNIQUE KEY uk_favorite_folder_active_default (user_id, default_active_key)');
CALL v20260707_favorite_add_index_if_missing('t_int_favorite_folder', 'uk_favorite_folder_active_name',
    'ALTER TABLE t_int_favorite_folder ADD UNIQUE KEY uk_favorite_folder_active_name (user_id, name, name_active_key)');

-- Backfill: create one default folder for users with active favorites still on
-- the legacy root folder and then attach those favorites to the default folder.
INSERT INTO t_int_favorite_folder (
    id, user_id, name, description, visibility, sort_order, post_count, is_default, create_time, update_time, is_deleted
)
SELECT
    f.user_id,
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
WHERE f.is_deleted = 0
  AND (f.folder_id IS NULL
   OR f.folder_id = 0);

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
DROP PROCEDURE IF EXISTS v20260707_favorite_precheck;
