-- High-value, low-risk integrity hardening for the Stage 2-5 schema.
-- Foreign keys are intentionally excluded because production orphan state is not assumed clean.
SET NAMES utf8mb4;

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260714_integrity_add_index_if_missing $$
CREATE PROCEDURE v20260714_integrity_add_index_if_missing(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64),
    IN p_columns VARCHAR(512),
    IN p_sql TEXT
)
BEGIN
    DECLARE v_columns VARCHAR(512) DEFAULT NULL;
    DECLARE v_min_non_unique INT DEFAULT NULL;
    DECLARE v_max_non_unique INT DEFAULT NULL;
    DECLARE v_error VARCHAR(128);

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
    ) THEN
        SET v_error = CONCAT('missing table for integrity index: ', p_table);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_error;
    END IF;

    SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ','),
           MIN(NON_UNIQUE),
           MAX(NON_UNIQUE)
      INTO v_columns, v_min_non_unique, v_max_non_unique
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = p_table
       AND INDEX_NAME = p_index;

    IF v_columns IS NULL THEN
        SET @ddl = p_sql;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    ELSEIF LOWER(v_columns) <> LOWER(p_columns)
        OR v_min_non_unique <> 1
        OR v_max_non_unique <> 1 THEN
        SET v_error = CONCAT('conflicting integrity index: ', p_table, '.', p_index);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_error;
    END IF;
END $$

DROP PROCEDURE IF EXISTS v20260714_integrity_add_check_if_missing $$
CREATE PROCEDURE v20260714_integrity_add_check_if_missing(
    IN p_table VARCHAR(64),
    IN p_constraint VARCHAR(64),
    IN p_normalized_clause TEXT,
    IN p_sql TEXT
)
BEGIN
    DECLARE v_clause TEXT DEFAULT NULL;
    DECLARE v_error VARCHAR(128);

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
    ) THEN
        SET v_error = CONCAT('missing table for integrity check: ', p_table);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_error;
    END IF;

    SELECT REPLACE(
               REPLACE(
                 REPLACE(
                   LOWER(
                     REPLACE(
                       REPLACE(
                         REPLACE(
                           REPLACE(
                             REPLACE(
                               REPLACE(
                                 REPLACE(
                                   REPLACE(COALESCE(cc.CHECK_CLAUSE, ''), ' ', ''),
                                   CHAR(9), ''
                                 ),
                                 CHAR(10), ''
                               ),
                               CHAR(13), ''
                             ),
                             '`', ''
                           ),
                           '(', ''
                         ),
                         ')', ''
                       ),
                       ',', ''
                     )
                   ),
                   '_utf8mb4', ''
                 ),
                 '_utf8', ''
               ),
               '_ascii', ''
           )
      INTO v_clause
      FROM information_schema.TABLE_CONSTRAINTS tc
      JOIN information_schema.CHECK_CONSTRAINTS cc
        ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
       AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
     WHERE tc.CONSTRAINT_SCHEMA = DATABASE()
       AND tc.TABLE_NAME = p_table
       AND tc.CONSTRAINT_NAME = p_constraint
       AND tc.CONSTRAINT_TYPE = 'CHECK';

    IF v_clause IS NULL THEN
        SET @ddl = p_sql;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    ELSEIF v_clause <> LOWER(p_normalized_clause) THEN
        SET v_error = CONCAT('conflicting integrity check: ', p_table, '.', p_constraint);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_error;
    END IF;
END $$

DROP PROCEDURE IF EXISTS v20260714_integrity_precheck $$
CREATE PROCEDURE v20260714_integrity_precheck()
BEGIN
    DECLARE v_required_table_count INT DEFAULT 0;

    SELECT COUNT(*)
      INTO v_required_table_count
      FROM information_schema.TABLES
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_TYPE = 'BASE TABLE'
       AND TABLE_NAME IN (
           't_incentive_freeze_record',
           't_incentive_invalidation_job',
           't_incentive_reward_rule',
           't_incentive_reconciliation_cursor',
           't_virtual_benefit_catalog',
           't_virtual_benefit_order',
           't_quota_bounty_submission',
           't_quota_bounty_appeal',
           't_community_role_definition'
       );

    IF v_required_table_count <> 9 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'database integrity hardening requires the 20260714 incentive tables';
    END IF;

    IF (
        SELECT COUNT(*)
        FROM t_incentive_reward_rule
        WHERE rule_version = 1
          AND rule_code IN (
              'ACCEPTED_ANSWER_POINT_V1',
              'ACCEPTED_ANSWER_REPUTATION_V1',
              'SUGGESTION_ACCEPTED_POINT_V1',
              'SUGGESTION_ACCEPTED_REPUTATION_V1',
              'FRESHNESS_UPDATED_POINT_V1',
              'FRESHNESS_UPDATED_REPUTATION_V1',
              'OPERATION_SELECTED_POINT_V1',
              'OPERATION_SELECTED_REPUTATION_V1',
              'REPORT_ACTION_TAKEN_POINT_V1',
              'REPORT_ACTION_TAKEN_REPUTATION_V1',
              'COLLAB_ACCEPTED_POINT_V1',
              'COLLAB_ACCEPTED_REPUTATION_V1',
              'FIRST_QUALIFIED_POST_POINT_V1',
              'FIRST_QUALIFIED_POST_REPUTATION_V1',
              'QUALIFIED_POST_POINT_V1',
              'HELPFUL_COMMENT_POINT_V1',
              'HELPFUL_COMMENT_REPUTATION_V1'
          )
    ) <> 17
    OR EXISTS (
        SELECT 1
        FROM t_incentive_reward_rule
        WHERE rule_version = 1
          AND rule_code IN (
              'ACCEPTED_ANSWER_POINT_V1',
              'ACCEPTED_ANSWER_REPUTATION_V1',
              'SUGGESTION_ACCEPTED_POINT_V1',
              'SUGGESTION_ACCEPTED_REPUTATION_V1',
              'FRESHNESS_UPDATED_POINT_V1',
              'FRESHNESS_UPDATED_REPUTATION_V1',
              'OPERATION_SELECTED_POINT_V1',
              'OPERATION_SELECTED_REPUTATION_V1',
              'REPORT_ACTION_TAKEN_POINT_V1',
              'REPORT_ACTION_TAKEN_REPUTATION_V1',
              'COLLAB_ACCEPTED_POINT_V1',
              'COLLAB_ACCEPTED_REPUTATION_V1',
              'FIRST_QUALIFIED_POST_POINT_V1',
              'FIRST_QUALIFIED_POST_REPUTATION_V1',
              'QUALIFIED_POST_POINT_V1',
              'HELPFUL_COMMENT_POINT_V1',
              'HELPFUL_COMMENT_REPUTATION_V1'
          )
          AND id <> 202607140100 + CASE rule_code
              WHEN 'ACCEPTED_ANSWER_POINT_V1' THEN 1
              WHEN 'ACCEPTED_ANSWER_REPUTATION_V1' THEN 2
              WHEN 'SUGGESTION_ACCEPTED_POINT_V1' THEN 3
              WHEN 'SUGGESTION_ACCEPTED_REPUTATION_V1' THEN 4
              WHEN 'FRESHNESS_UPDATED_POINT_V1' THEN 5
              WHEN 'FRESHNESS_UPDATED_REPUTATION_V1' THEN 6
              WHEN 'OPERATION_SELECTED_POINT_V1' THEN 7
              WHEN 'OPERATION_SELECTED_REPUTATION_V1' THEN 8
              WHEN 'REPORT_ACTION_TAKEN_POINT_V1' THEN 9
              WHEN 'REPORT_ACTION_TAKEN_REPUTATION_V1' THEN 10
              WHEN 'COLLAB_ACCEPTED_POINT_V1' THEN 11
              WHEN 'COLLAB_ACCEPTED_REPUTATION_V1' THEN 12
              WHEN 'FIRST_QUALIFIED_POST_POINT_V1' THEN 13
              WHEN 'FIRST_QUALIFIED_POST_REPUTATION_V1' THEN 14
              WHEN 'QUALIFIED_POST_POINT_V1' THEN 15
              WHEN 'HELPFUL_COMMENT_POINT_V1' THEN 16
              WHEN 'HELPFUL_COMMENT_REPUTATION_V1' THEN 17
          END
        LIMIT 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'repair incentive reward rule seed identity conflicts before hardening';
    END IF;

    IF (
        SELECT COUNT(*)
        FROM t_virtual_benefit_catalog
        WHERE benefit_code IN (
            'PROFILE_THEME_UNLOCK',
            'IDENTITY_CARD_STYLE',
            'SERIES_LAYOUT_TEMPLATE',
            'ACTIVITY_BADGE',
            'AI_ASSIST_QUOTA',
            'CAMPAIGN_DEPOSIT',
            'COLLECTION_ORGANIZATION_QUOTA'
        )
    ) <> 7
    OR EXISTS (
        SELECT 1
        FROM t_virtual_benefit_catalog
        WHERE benefit_code IN (
            'PROFILE_THEME_UNLOCK',
            'IDENTITY_CARD_STYLE',
            'SERIES_LAYOUT_TEMPLATE',
            'ACTIVITY_BADGE',
            'AI_ASSIST_QUOTA',
            'CAMPAIGN_DEPOSIT',
            'COLLECTION_ORGANIZATION_QUOTA'
        )
          AND id <> 202607144000 + CASE benefit_code
              WHEN 'PROFILE_THEME_UNLOCK' THEN 1
              WHEN 'IDENTITY_CARD_STYLE' THEN 2
              WHEN 'SERIES_LAYOUT_TEMPLATE' THEN 3
              WHEN 'ACTIVITY_BADGE' THEN 4
              WHEN 'AI_ASSIST_QUOTA' THEN 5
              WHEN 'CAMPAIGN_DEPOSIT' THEN 6
              WHEN 'COLLECTION_ORGANIZATION_QUOTA' THEN 7
          END
        LIMIT 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'repair virtual benefit seed identity conflicts before hardening';
    END IF;

    IF (
        SELECT COUNT(*)
        FROM t_community_role_definition
        WHERE role_code IN (
            'COLLABORATION_INITIATOR',
            'TOPIC_CANDIDATE_RECOMMENDER',
            'CHANNEL_RESOURCE_MAINTAINER',
            'LOW_RISK_CANDIDATE_SCOUT',
            'TOPIC_STEWARD',
            'CHANNEL_CURATOR',
            'CAMPAIGN_CONTRIBUTOR',
            'COMMUNITY_RULE_PROPOSER'
        )
          AND domain_code IN ('TECH', 'CAREER', 'READING', 'LIFESTYLE')
    ) <> 32
    OR EXISTS (
        SELECT 1
        FROM t_community_role_definition
        WHERE role_code IN (
            'COLLABORATION_INITIATOR',
            'TOPIC_CANDIDATE_RECOMMENDER',
            'CHANNEL_RESOURCE_MAINTAINER',
            'LOW_RISK_CANDIDATE_SCOUT',
            'TOPIC_STEWARD',
            'CHANNEL_CURATOR',
            'CAMPAIGN_CONTRIBUTOR',
            'COMMUNITY_RULE_PROPOSER'
        )
          AND domain_code IN ('TECH', 'CAREER', 'READING', 'LIFESTYLE')
          AND id <> 202607145000
              + CASE role_code
                  WHEN 'COLLABORATION_INITIATOR' THEN 10
                  WHEN 'TOPIC_CANDIDATE_RECOMMENDER' THEN 20
                  WHEN 'CHANNEL_RESOURCE_MAINTAINER' THEN 30
                  WHEN 'LOW_RISK_CANDIDATE_SCOUT' THEN 40
                  WHEN 'TOPIC_STEWARD' THEN 50
                  WHEN 'CHANNEL_CURATOR' THEN 60
                  WHEN 'CAMPAIGN_CONTRIBUTOR' THEN 70
                  WHEN 'COMMUNITY_RULE_PROPOSER' THEN 80
              END
              + CASE domain_code
                  WHEN 'TECH' THEN 1
                  WHEN 'CAREER' THEN 2
                  WHEN 'READING' THEN 3
                  WHEN 'LIFESTYLE' THEN 4
              END
        LIMIT 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'repair community role seed identity conflicts before hardening';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM t_incentive_reconciliation_cursor
        WHERE id = 1
        LIMIT 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'repair missing incentive reconciliation cursor before hardening';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM t_virtual_benefit_catalog
        WHERE NOT (
            (total_stock IS NULL AND available_stock IS NULL)
            OR (
                total_stock IS NOT NULL
                AND available_stock IS NOT NULL
                AND total_stock >= 0
                AND available_stock >= 0
                AND available_stock <= total_stock
            )
        )
        LIMIT 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'repair t_virtual_benefit_catalog stock NULL/range combinations before hardening';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM t_virtual_benefit_order
        WHERE quantity <= 0
           OR unit_point_cost <= 0
           OR total_point_cost <= 0
           OR CAST(total_point_cost AS DECIMAL(65, 0))
              <> CAST(quantity AS DECIMAL(65, 0)) * CAST(unit_point_cost AS DECIMAL(65, 0))
        LIMIT 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'repair t_virtual_benefit_order quantity/cost product rows before hardening';
    END IF;
END $$

DELIMITER ;

CALL v20260714_integrity_precheck();

CALL v20260714_integrity_add_index_if_missing(
    't_incentive_freeze_record',
    'idx_incentive_freeze_account_status',
    'account_id,freeze_status,blocks_spending,freeze_amount',
    'ALTER TABLE t_incentive_freeze_record ADD KEY idx_incentive_freeze_account_status (account_id, freeze_status, blocks_spending, freeze_amount)'
);
CALL v20260714_integrity_add_index_if_missing(
    't_incentive_invalidation_job',
    'idx_incentive_invalidation_reference',
    'reference_type,reference_id',
    'ALTER TABLE t_incentive_invalidation_job ADD KEY idx_incentive_invalidation_reference (reference_type, reference_id)'
);
CALL v20260714_integrity_add_index_if_missing(
    't_virtual_benefit_order',
    'idx_virtual_benefit_order_benefit',
    'benefit_id',
    'ALTER TABLE t_virtual_benefit_order ADD KEY idx_virtual_benefit_order_benefit (benefit_id)'
);
CALL v20260714_integrity_add_index_if_missing(
    't_quota_bounty_submission',
    'idx_quota_submission_applicant',
    'applicant_uid,create_time,id',
    'ALTER TABLE t_quota_bounty_submission ADD KEY idx_quota_submission_applicant (applicant_uid, create_time, id)'
);
CALL v20260714_integrity_add_index_if_missing(
    't_quota_bounty_appeal',
    'idx_quota_bounty_appeal_applicant',
    'applicant_uid,create_time,id',
    'ALTER TABLE t_quota_bounty_appeal ADD KEY idx_quota_bounty_appeal_applicant (applicant_uid, create_time, id)'
);

CALL v20260714_integrity_add_check_if_missing(
    't_virtual_benefit_catalog',
    'chk_virtual_benefit_stock_null_pair',
    'total_stockisnullandavailable_stockisnullortotal_stockisnotnullandavailable_stockisnotnullandtotal_stock>=0andavailable_stock>=0andavailable_stock<=total_stock',
    'ALTER TABLE t_virtual_benefit_catalog ADD CONSTRAINT chk_virtual_benefit_stock_null_pair CHECK ((total_stock IS NULL AND available_stock IS NULL) OR (total_stock IS NOT NULL AND available_stock IS NOT NULL AND total_stock >= 0 AND available_stock >= 0 AND available_stock <= total_stock))'
);
CALL v20260714_integrity_add_check_if_missing(
    't_virtual_benefit_order',
    'chk_virtual_benefit_order_cost',
    'quantity>0andunit_point_cost>0andtotal_point_cost>0andcasttotal_point_costasdecimal650=castquantityasdecimal650*castunit_point_costasdecimal650',
    'ALTER TABLE t_virtual_benefit_order ADD CONSTRAINT chk_virtual_benefit_order_cost CHECK (quantity > 0 AND unit_point_cost > 0 AND total_point_cost > 0 AND CAST(total_point_cost AS DECIMAL(65, 0)) = CAST(quantity AS DECIMAL(65, 0)) * CAST(unit_point_cost AS DECIMAL(65, 0)))'
);

DROP PROCEDURE IF EXISTS v20260714_integrity_add_index_if_missing;
DROP PROCEDURE IF EXISTS v20260714_integrity_add_check_if_missing;
DROP PROCEDURE IF EXISTS v20260714_integrity_precheck;
