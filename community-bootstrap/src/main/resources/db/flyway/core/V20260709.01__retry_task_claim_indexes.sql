-- Cover retry-task claim queries ordered by create_time/id.

SET @schema_name = DATABASE();

SET @idx_exists = (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 't_notif_retry_task'
      AND index_name = 'idx_notif_retry_claim'
);
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE t_notif_retry_task ADD KEY idx_notif_retry_claim (task_status, next_retry_time, create_time, id)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 't_notif_retry_task'
      AND index_name = 'idx_notif_retry_expired_claim'
);
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE t_notif_retry_task ADD KEY idx_notif_retry_expired_claim (task_status, lock_until, create_time, id)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 't_search_index_retry_task'
      AND index_name = 'idx_search_index_retry_claim'
);
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE t_search_index_retry_task ADD KEY idx_search_index_retry_claim (task_status, next_retry_time, create_time, id)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 't_search_index_retry_task'
      AND index_name = 'idx_search_index_retry_expired_claim'
);
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE t_search_index_retry_task ADD KEY idx_search_index_retry_expired_claim (task_status, lock_until, create_time, id)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 't_question_index_retry_task'
      AND index_name = 'idx_question_index_retry_claim'
);
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE t_question_index_retry_task ADD KEY idx_question_index_retry_claim (task_status, next_retry_time, create_time, id)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 't_question_index_retry_task'
      AND index_name = 'idx_question_index_retry_expired_claim'
);
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE t_question_index_retry_task ADD KEY idx_question_index_retry_expired_claim (task_status, lock_until, create_time, id)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
