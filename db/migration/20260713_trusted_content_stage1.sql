-- Trusted content stage 1: question/freshness state, useful feedback,
-- private content suggestions, public update metadata, and idempotent growth events.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS t_int_post_trust_state (
    post_id             BIGINT       NOT NULL,
    question_status     VARCHAR(32)  NOT NULL DEFAULT 'OPEN',
    accepted_comment_id BIGINT       NULL,
    duplicate_post_id   BIGINT       NULL,
    freshness_status    VARCHAR(32)  NOT NULL DEFAULT 'CURRENT',
    successor_post_id   BIGINT       NULL,
    last_confirmed_at   DATETIME(3)  NULL,
    suggestions_open    TINYINT      NOT NULL DEFAULT 1,
    create_time         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (post_id),
    KEY idx_trust_state_question_status (question_status, update_time, post_id),
    KEY idx_trust_state_freshness_status (freshness_status, update_time, post_id),
    KEY idx_trust_state_accepted_comment (accepted_comment_id),
    KEY idx_trust_state_duplicate_post (duplicate_post_id),
    KEY idx_trust_state_successor_post (successor_post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Post question and freshness trust state';

CREATE TABLE IF NOT EXISTS t_int_post_useful_feedback (
    id             BIGINT       NOT NULL,
    user_id        BIGINT       NOT NULL,
    post_id        BIGINT       NOT NULL,
    post_author_id BIGINT       NOT NULL,
    reason         VARCHAR(32)  NOT NULL,
    create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_post (user_id, post_id),
    KEY idx_useful_feedback_post_reason (post_id, reason),
    KEY idx_useful_feedback_author_time (post_author_id, create_time, post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='One useful-content reason per user and post';

CREATE TABLE IF NOT EXISTS t_int_content_suggestion (
    id                       BIGINT        NOT NULL,
    post_id                  BIGINT        NOT NULL,
    post_author_id           BIGINT        NOT NULL,
    submitter_uid            BIGINT        NOT NULL,
    suggestion_type          VARCHAR(32)   NOT NULL,
    detail                   VARCHAR(2000) NOT NULL,
    normalized_content_hash  CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_url               VARCHAR(1000) NULL,
    allow_public_attribution TINYINT       NOT NULL DEFAULT 0,
    decision                 VARCHAR(32)   NULL,
    author_reply             VARCHAR(1000) NULL,
    public_note              VARCHAR(500)  NULL,
    result_version           INT           NULL,
    pending_dedup_key        CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    pending_guard            TINYINT
        GENERATED ALWAYS AS (
            CASE WHEN decision IS NULL THEN 1 ELSE NULL END
        ) STORED,
    decided_at               DATETIME(3)   NULL,
    create_time              DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time              DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_pending_suggestion (
        post_id,
        submitter_uid,
        suggestion_type,
        normalized_content_hash,
        pending_guard
    ),
    KEY idx_content_suggestion_mine_time (post_id, submitter_uid, update_time, id),
    KEY idx_content_suggestion_mine_decided (post_id, submitter_uid, decided_at, id),
    KEY idx_content_suggestion_author_pending (post_author_id, decision, update_time, id),
    KEY idx_content_suggestion_author_time (post_id, update_time, id),
    KEY idx_content_suggestion_public (post_id, decided_at, id),
    KEY idx_content_suggestion_type_status (post_id, suggestion_type, decision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Private content corrections and author decisions';

-- Backfill only answered community questions; unanswered posts remain lazily initialized as OPEN.
INSERT INTO t_int_post_trust_state (
    post_id,
    question_status
)
SELECT p.id,
       'ANSWERED'
FROM t_post_main p
WHERE p.post_type = 13
  AND p.is_deleted = 0
  AND EXISTS (
      SELECT 1
      FROM t_int_comment c
      WHERE c.post_id = p.id
        AND c.root_id = 0
        AND c.comment_status = 1
        AND c.is_deleted = 0
  )
  AND NOT EXISTS (
      SELECT 1
      FROM t_int_post_trust_state s
      WHERE s.post_id = p.id
  );

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260713_trusted_content_ensure_column_definition $$
CREATE PROCEDURE v20260713_trusted_content_ensure_column_definition(
    IN p_table VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_expected_column_type VARCHAR(255),
    IN p_expected_nullable VARCHAR(3),
    IN p_expected_character_set VARCHAR(64),
    IN p_expected_collation VARCHAR(64),
    IN p_definition TEXT,
    IN p_position_clause VARCHAR(255)
)
BEGIN
    DECLARE v_column_type VARCHAR(255) DEFAULT NULL;
    DECLARE v_is_nullable VARCHAR(3) DEFAULT NULL;
    DECLARE v_character_set_name VARCHAR(64) DEFAULT NULL;
    DECLARE v_collation_name VARCHAR(64) DEFAULT NULL;

    IF EXISTS (
        SELECT 1
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
    ) THEN
        SELECT MAX(COLUMN_TYPE),
               MAX(IS_NULLABLE),
               MAX(CHARACTER_SET_NAME),
               MAX(COLLATION_NAME)
          INTO v_column_type,
               v_is_nullable,
               v_character_set_name,
               v_collation_name
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND COLUMN_NAME = p_column;

        IF v_column_type IS NULL THEN
            SET @ddl = CONCAT(
                'ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ',
                p_definition,
                COALESCE(p_position_clause, '')
            );
            PREPARE stmt FROM @ddl;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        ELSEIF LOWER(v_column_type) <> LOWER(p_expected_column_type)
            OR UPPER(v_is_nullable) <> UPPER(p_expected_nullable)
            OR (
                p_expected_character_set IS NOT NULL
                AND NOT (
                    LOWER(v_character_set_name)
                    <=> LOWER(p_expected_character_set)
                )
            )
            OR (
                p_expected_collation IS NOT NULL
                AND NOT (
                    LOWER(v_collation_name)
                    <=> LOWER(p_expected_collation)
                )
            ) THEN
            SET @ddl = CONCAT(
                'ALTER TABLE `', p_table, '` MODIFY COLUMN `', p_column, '` ',
                p_definition,
                COALESCE(p_position_clause, '')
            );
            PREPARE stmt FROM @ddl;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
    END IF;
END $$

DROP PROCEDURE IF EXISTS v20260713_trusted_content_ensure_pending_guard $$
CREATE PROCEDURE v20260713_trusted_content_ensure_pending_guard()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 't_int_content_suggestion'
          AND INDEX_NAME = 'uk_pending_suggestion'
    ) THEN
        ALTER TABLE t_int_content_suggestion
            DROP INDEX uk_pending_suggestion;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 't_int_content_suggestion'
          AND COLUMN_NAME = 'pending_guard'
    ) THEN
        ALTER TABLE t_int_content_suggestion
            MODIFY COLUMN pending_guard TINYINT
                GENERATED ALWAYS AS (
                    CASE WHEN decision IS NULL THEN 1 ELSE NULL END
                ) STORED;
    ELSE
        ALTER TABLE t_int_content_suggestion
            ADD COLUMN pending_guard TINYINT
                GENERATED ALWAYS AS (
                    CASE WHEN decision IS NULL THEN 1 ELSE NULL END
                ) STORED
                AFTER pending_dedup_key;
    END IF;
END $$

DROP PROCEDURE IF EXISTS v20260713_trusted_content_ensure_index $$
CREATE PROCEDURE v20260713_trusted_content_ensure_index(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64),
    IN p_expected_columns VARCHAR(512),
    IN p_unique TINYINT,
    IN p_add_clause TEXT
)
BEGIN
    DECLARE v_actual_columns VARCHAR(512);
    DECLARE v_non_unique INT DEFAULT NULL;
    DECLARE v_expected_non_unique INT DEFAULT 1;

    SET v_expected_non_unique = IF(p_unique = 1, 0, 1);

    SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ','),
           MIN(NON_UNIQUE)
      INTO v_actual_columns, v_non_unique
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = p_table
       AND INDEX_NAME = p_index;

    IF v_actual_columns IS NULL THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD ', p_add_clause);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    ELSEIF v_non_unique <> v_expected_non_unique OR v_actual_columns <> p_expected_columns THEN
        SET @ddl = CONCAT(
            'ALTER TABLE `', p_table, '` DROP INDEX `', p_index, '`, ADD ', p_add_clause
        );
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DROP PROCEDURE IF EXISTS v20260713_trusted_content_ensure_foreign_key $$
CREATE PROCEDURE v20260713_trusted_content_ensure_foreign_key(
    IN p_table VARCHAR(64),
    IN p_constraint VARCHAR(64),
    IN p_expected_columns VARCHAR(512),
    IN p_referenced_table VARCHAR(64),
    IN p_expected_referenced_columns VARCHAR(512),
    IN p_delete_rule VARCHAR(16),
    IN p_update_rule VARCHAR(16),
    IN p_add_clause TEXT
)
BEGIN
    DECLARE v_actual_columns VARCHAR(512);
    DECLARE v_actual_referenced_table VARCHAR(64);
    DECLARE v_actual_referenced_columns VARCHAR(512);
    DECLARE v_actual_delete_rule VARCHAR(16);
    DECLARE v_actual_update_rule VARCHAR(16);

    SELECT GROUP_CONCAT(kcu.COLUMN_NAME ORDER BY kcu.ORDINAL_POSITION SEPARATOR ','),
           MAX(kcu.REFERENCED_TABLE_NAME),
           GROUP_CONCAT(kcu.REFERENCED_COLUMN_NAME ORDER BY kcu.ORDINAL_POSITION SEPARATOR ','),
           MAX(rc.DELETE_RULE),
           MAX(rc.UPDATE_RULE)
      INTO v_actual_columns,
           v_actual_referenced_table,
           v_actual_referenced_columns,
           v_actual_delete_rule,
           v_actual_update_rule
      FROM information_schema.KEY_COLUMN_USAGE kcu
      LEFT JOIN information_schema.REFERENTIAL_CONSTRAINTS rc
        ON rc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA
       AND rc.TABLE_NAME = kcu.TABLE_NAME
       AND rc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME
     WHERE kcu.CONSTRAINT_SCHEMA = DATABASE()
       AND kcu.TABLE_NAME = p_table
       AND kcu.CONSTRAINT_NAME = p_constraint
       AND kcu.REFERENCED_TABLE_NAME IS NOT NULL;

    IF v_actual_columns IS NULL THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD ', p_add_clause);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    ELSEIF v_actual_columns <> p_expected_columns
        OR v_actual_referenced_table <> p_referenced_table
        OR v_actual_referenced_columns <> p_expected_referenced_columns
        OR v_actual_delete_rule <> p_delete_rule
        OR v_actual_update_rule <> p_update_rule THEN
        SET @ddl = CONCAT(
            'ALTER TABLE `', p_table, '` DROP FOREIGN KEY `', p_constraint, '`, ADD ', p_add_clause
        );
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DROP PROCEDURE IF EXISTS v20260713_trusted_content_assert_no_duplicates $$
CREATE PROCEDURE v20260713_trusted_content_assert_no_duplicates()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM (
            SELECT user_id, post_id
            FROM t_int_post_useful_feedback
            GROUP BY user_id, post_id
            HAVING COUNT(*) > 1
            LIMIT 1
        ) duplicates
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'duplicate useful feedback rows must be repaired before adding uk_user_post';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM (
            SELECT post_id,
                   submitter_uid,
                   suggestion_type,
                   normalized_content_hash
            FROM t_int_content_suggestion
            WHERE decision IS NULL
            GROUP BY post_id,
                     submitter_uid,
                     suggestion_type,
                     normalized_content_hash
            HAVING COUNT(*) > 1
            LIMIT 1
        ) duplicates
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'duplicate pending content suggestions must be repaired before adding uk_pending_suggestion';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM (
            SELECT post_id, result_version
            FROM t_post_version_history
            WHERE result_version IS NOT NULL
            GROUP BY post_id, result_version
            HAVING COUNT(*) > 1
            LIMIT 1
        ) duplicates
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'duplicate post result versions must be repaired before adding uk_post_result_version';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM (
            SELECT event_key
            FROM t_growth_event
            WHERE event_key IS NOT NULL
            GROUP BY event_key
            HAVING COUNT(*) > 1
            LIMIT 1
        ) duplicates
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'duplicate growth event keys must be repaired before adding uk_growth_event_key';
    END IF;
END $$

DELIMITER ;

CALL v20260713_trusted_content_ensure_column_definition(
    't_post_version_history',
    'result_version',
    'int',
    'YES',
    NULL,
    NULL,
    'INT NULL',
    ' AFTER `base_version`'
);
CALL v20260713_trusted_content_ensure_column_definition(
    't_post_version_history',
    'public_update_summary',
    'varchar(500)',
    'YES',
    NULL,
    NULL,
    'VARCHAR(500) NULL',
    ' AFTER `change_summary`'
);
CALL v20260713_trusted_content_ensure_column_definition(
    't_post_version_history',
    'impact_scope',
    'varchar(255)',
    'YES',
    NULL,
    NULL,
    'VARCHAR(255) NULL',
    ' AFTER `public_update_summary`'
);
CALL v20260713_trusted_content_ensure_column_definition(
    't_growth_event',
    'event_key',
    'varchar(128)',
    'YES',
    'ascii',
    'ascii_bin',
    'VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL',
    ' AFTER `id`'
);
CALL v20260713_trusted_content_ensure_column_definition(
    't_int_content_suggestion',
    'normalized_content_hash',
    'char(64)',
    'NO',
    'ascii',
    'ascii_bin',
    'CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL',
    ' AFTER `detail`'
);
CALL v20260713_trusted_content_ensure_column_definition(
    't_int_content_suggestion',
    'decision',
    'varchar(32)',
    'YES',
    NULL,
    NULL,
    'VARCHAR(32) NULL',
    ' AFTER `allow_public_attribution`'
);
CALL v20260713_trusted_content_ensure_column_definition(
    't_int_content_suggestion',
    'result_version',
    'int',
    'YES',
    NULL,
    NULL,
    'INT NULL',
    ' AFTER `public_note`'
);
CALL v20260713_trusted_content_ensure_column_definition(
    't_int_content_suggestion',
    'pending_dedup_key',
    'char(64)',
    'YES',
    'ascii',
    'ascii_bin',
    'CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL',
    ' AFTER `result_version`'
);

CALL v20260713_trusted_content_assert_no_duplicates();
CALL v20260713_trusted_content_ensure_pending_guard();

CALL v20260713_trusted_content_ensure_index(
    't_post_version_history',
    'uk_post_result_version',
    'post_id,result_version',
    1,
    'UNIQUE KEY `uk_post_result_version` (`post_id`, `result_version`)'
);
CALL v20260713_trusted_content_ensure_index(
    't_post_version_history',
    'idx_post_public_update',
    'post_id,result_version,create_time,id',
    0,
    'KEY `idx_post_public_update` (`post_id`, `result_version`, `create_time`, `id`)'
);
CALL v20260713_trusted_content_ensure_index(
    't_growth_event',
    'uk_growth_event_key',
    'event_key',
    1,
    'UNIQUE KEY `uk_growth_event_key` (`event_key`)'
);
CALL v20260713_trusted_content_ensure_index(
    't_int_post_trust_state',
    'idx_trust_state_question_status',
    'question_status,update_time,post_id',
    0,
    'KEY `idx_trust_state_question_status` (`question_status`, `update_time`, `post_id`)'
);
CALL v20260713_trusted_content_ensure_index(
    't_int_post_trust_state',
    'idx_trust_state_freshness_status',
    'freshness_status,update_time,post_id',
    0,
    'KEY `idx_trust_state_freshness_status` (`freshness_status`, `update_time`, `post_id`)'
);
CALL v20260713_trusted_content_ensure_index(
    't_int_post_trust_state',
    'idx_trust_state_accepted_comment',
    'accepted_comment_id',
    0,
    'KEY `idx_trust_state_accepted_comment` (`accepted_comment_id`)'
);
CALL v20260713_trusted_content_ensure_index(
    't_int_post_trust_state',
    'idx_trust_state_duplicate_post',
    'duplicate_post_id',
    0,
    'KEY `idx_trust_state_duplicate_post` (`duplicate_post_id`)'
);
CALL v20260713_trusted_content_ensure_index(
    't_int_post_trust_state',
    'idx_trust_state_successor_post',
    'successor_post_id',
    0,
    'KEY `idx_trust_state_successor_post` (`successor_post_id`)'
);
CALL v20260713_trusted_content_ensure_index(
    't_int_post_useful_feedback',
    'uk_user_post',
    'user_id,post_id',
    1,
    'UNIQUE KEY `uk_user_post` (`user_id`, `post_id`)'
);
CALL v20260713_trusted_content_ensure_index(
    't_int_post_useful_feedback',
    'idx_useful_feedback_post_reason',
    'post_id,reason',
    0,
    'KEY `idx_useful_feedback_post_reason` (`post_id`, `reason`)'
);
CALL v20260713_trusted_content_ensure_index(
    't_int_post_useful_feedback',
    'idx_useful_feedback_author_time',
    'post_author_id,create_time,post_id',
    0,
    'KEY `idx_useful_feedback_author_time` (`post_author_id`, `create_time`, `post_id`)'
);
CALL v20260713_trusted_content_ensure_index(
    't_int_content_suggestion',
    'uk_pending_suggestion',
    'post_id,submitter_uid,suggestion_type,normalized_content_hash,pending_guard',
    1,
    'UNIQUE KEY `uk_pending_suggestion` (`post_id`, `submitter_uid`, `suggestion_type`, `normalized_content_hash`, `pending_guard`)'
);
CALL v20260713_trusted_content_ensure_index(
    't_int_content_suggestion',
    'idx_content_suggestion_mine_time',
    'post_id,submitter_uid,update_time,id',
    0,
    'KEY `idx_content_suggestion_mine_time` (`post_id`, `submitter_uid`, `update_time`, `id`)'
);
CALL v20260713_trusted_content_ensure_index(
    't_int_content_suggestion',
    'idx_content_suggestion_mine_decided',
    'post_id,submitter_uid,decided_at,id',
    0,
    'KEY `idx_content_suggestion_mine_decided` (`post_id`, `submitter_uid`, `decided_at`, `id`)'
);
CALL v20260713_trusted_content_ensure_index(
    't_int_content_suggestion',
    'idx_content_suggestion_author_pending',
    'post_author_id,decision,update_time,id',
    0,
    'KEY `idx_content_suggestion_author_pending` (`post_author_id`, `decision`, `update_time`, `id`)'
);
CALL v20260713_trusted_content_ensure_index(
    't_int_content_suggestion',
    'idx_content_suggestion_author_time',
    'post_id,update_time,id',
    0,
    'KEY `idx_content_suggestion_author_time` (`post_id`, `update_time`, `id`)'
);
CALL v20260713_trusted_content_ensure_index(
    't_int_content_suggestion',
    'idx_content_suggestion_public',
    'post_id,decided_at,id',
    0,
    'KEY `idx_content_suggestion_public` (`post_id`, `decided_at`, `id`)'
);
CALL v20260713_trusted_content_ensure_index(
    't_int_content_suggestion',
    'idx_content_suggestion_type_status',
    'post_id,suggestion_type,decision',
    0,
    'KEY `idx_content_suggestion_type_status` (`post_id`, `suggestion_type`, `decision`)'
);

CALL v20260713_trusted_content_ensure_foreign_key(
    't_int_post_trust_state',
    'fk_trust_state_post',
    'post_id',
    't_post_main',
    'id',
    'CASCADE',
    'RESTRICT',
    'CONSTRAINT `fk_trust_state_post` FOREIGN KEY (`post_id`) REFERENCES `t_post_main` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT'
);
CALL v20260713_trusted_content_ensure_foreign_key(
    't_int_post_trust_state',
    'fk_trust_state_accepted_comment',
    'accepted_comment_id',
    't_int_comment',
    'id',
    'SET NULL',
    'RESTRICT',
    'CONSTRAINT `fk_trust_state_accepted_comment` FOREIGN KEY (`accepted_comment_id`) REFERENCES `t_int_comment` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT'
);
CALL v20260713_trusted_content_ensure_foreign_key(
    't_int_post_trust_state',
    'fk_trust_state_duplicate_post',
    'duplicate_post_id',
    't_post_main',
    'id',
    'SET NULL',
    'RESTRICT',
    'CONSTRAINT `fk_trust_state_duplicate_post` FOREIGN KEY (`duplicate_post_id`) REFERENCES `t_post_main` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT'
);
CALL v20260713_trusted_content_ensure_foreign_key(
    't_int_post_trust_state',
    'fk_trust_state_successor_post',
    'successor_post_id',
    't_post_main',
    'id',
    'SET NULL',
    'RESTRICT',
    'CONSTRAINT `fk_trust_state_successor_post` FOREIGN KEY (`successor_post_id`) REFERENCES `t_post_main` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT'
);
CALL v20260713_trusted_content_ensure_foreign_key(
    't_int_post_useful_feedback',
    'fk_useful_feedback_post',
    'post_id',
    't_post_main',
    'id',
    'CASCADE',
    'RESTRICT',
    'CONSTRAINT `fk_useful_feedback_post` FOREIGN KEY (`post_id`) REFERENCES `t_post_main` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT'
);
CALL v20260713_trusted_content_ensure_foreign_key(
    't_int_post_useful_feedback',
    'fk_useful_feedback_user',
    'user_id',
    't_user_account',
    'id',
    'CASCADE',
    'RESTRICT',
    'CONSTRAINT `fk_useful_feedback_user` FOREIGN KEY (`user_id`) REFERENCES `t_user_account` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT'
);
CALL v20260713_trusted_content_ensure_foreign_key(
    't_int_content_suggestion',
    'fk_content_suggestion_post',
    'post_id',
    't_post_main',
    'id',
    'CASCADE',
    'RESTRICT',
    'CONSTRAINT `fk_content_suggestion_post` FOREIGN KEY (`post_id`) REFERENCES `t_post_main` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT'
);
CALL v20260713_trusted_content_ensure_foreign_key(
    't_int_content_suggestion',
    'fk_content_suggestion_submitter',
    'submitter_uid',
    't_user_account',
    'id',
    'RESTRICT',
    'RESTRICT',
    'CONSTRAINT `fk_content_suggestion_submitter` FOREIGN KEY (`submitter_uid`) REFERENCES `t_user_account` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT'
);
CALL v20260713_trusted_content_ensure_foreign_key(
    't_int_content_suggestion',
    'fk_content_suggestion_decider',
    'post_author_id',
    't_user_account',
    'id',
    'RESTRICT',
    'RESTRICT',
    'CONSTRAINT `fk_content_suggestion_decider` FOREIGN KEY (`post_author_id`) REFERENCES `t_user_account` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT'
);
CALL v20260713_trusted_content_ensure_foreign_key(
    't_int_content_suggestion',
    'fk_content_suggestion_result_version',
    'post_id,result_version',
    't_post_version_history',
    'post_id,result_version',
    'RESTRICT',
    'RESTRICT',
    'CONSTRAINT `fk_content_suggestion_result_version` FOREIGN KEY (`post_id`, `result_version`) REFERENCES `t_post_version_history` (`post_id`, `result_version`) ON DELETE RESTRICT ON UPDATE RESTRICT'
);

DROP PROCEDURE IF EXISTS v20260713_trusted_content_ensure_column_definition;
DROP PROCEDURE IF EXISTS v20260713_trusted_content_ensure_pending_guard;
DROP PROCEDURE IF EXISTS v20260713_trusted_content_ensure_index;
DROP PROCEDURE IF EXISTS v20260713_trusted_content_ensure_foreign_key;
DROP PROCEDURE IF EXISTS v20260713_trusted_content_assert_no_duplicates;
