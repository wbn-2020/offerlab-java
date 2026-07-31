-- V22 local-only refresh for an existing OfferLab demo database.
-- Run from the repository root with the MySQL client after applying the
-- current schema migrations. This file is intentionally outside db/migration
-- so immutable migration history and production Flyway behavior stay unchanged.
SET NAMES utf8mb4;

-- Personalized prep records always stay on the dedicated demo identity. This
-- wrapper never guesses an existing admin or first active user.
SET @offerlab_demo_user_uid := 990000000000000001;
SET @offerlab_demo_refresh_original_autocommit := @@SESSION.autocommit;

DROP TEMPORARY TABLE IF EXISTS offerlab_demo_refresh_assertion;
CREATE TEMPORARY TABLE offerlab_demo_refresh_assertion (
    assertion_name VARCHAR(96) NOT NULL,
    failure_count BIGINT NOT NULL,
    CONSTRAINT chk_offerlab_demo_refresh_succeeded CHECK (failure_count = 0)
) ENGINE=InnoDB;

SET SESSION autocommit = 0;
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
START TRANSACTION;

-- Stop before changing data when deterministic ids or per-user unique keys
-- already represent unrelated rows.
SET @offerlab_demo_refresh_identity_conflicts := (
    SELECT COUNT(*)
    FROM t_user_prep_target
    WHERE (id = 990300000000000001 AND (
              NOT (uid <=> @offerlab_demo_user_uid)
              OR NOT (target_type <=> 'company')
              OR NOT (target_value <=> '深测科技')
          ))
       OR (id = 990300000000000002 AND (
              NOT (uid <=> @offerlab_demo_user_uid)
              OR NOT (target_type <=> 'position')
              OR NOT (target_value <=> 'Java 后端')
          ))
       OR (id = 990300000000000003 AND (
              NOT (uid <=> @offerlab_demo_user_uid)
              OR NOT (target_type <=> 'tag')
              OR NOT (target_value <=> 'Kafka')
          ))
       OR (uid = @offerlab_demo_user_uid AND target_type = 'company' AND target_value = '深测科技' AND id <> 990300000000000001)
       OR (uid = @offerlab_demo_user_uid AND target_type = 'position' AND target_value = 'Java 后端' AND id <> 990300000000000002)
       OR (uid = @offerlab_demo_user_uid AND target_type = 'tag' AND target_value = 'Kafka' AND id <> 990300000000000003)
) + (
    SELECT COUNT(*)
    FROM t_user_question_progress
    WHERE (id = 990310000000000001 AND (NOT (uid <=> @offerlab_demo_user_uid) OR NOT (question_id <=> 990200000000000001)))
       OR (id = 990310000000000002 AND (NOT (uid <=> @offerlab_demo_user_uid) OR NOT (question_id <=> 990200000000000002)))
       OR (id = 990310000000000003 AND (NOT (uid <=> @offerlab_demo_user_uid) OR NOT (question_id <=> 990200000000000003)))
       OR (id = 990310000000000004 AND (NOT (uid <=> @offerlab_demo_user_uid) OR NOT (question_id <=> 990200000000000004)))
       OR (id = 990310000000000005 AND (NOT (uid <=> @offerlab_demo_user_uid) OR NOT (question_id <=> 990200000000000005)))
       OR (id = 990310000000000006 AND (NOT (uid <=> @offerlab_demo_user_uid) OR NOT (question_id <=> 990200000000000006)))
       OR (id = 990310000000000007 AND (NOT (uid <=> @offerlab_demo_user_uid) OR NOT (question_id <=> 990200000000000007)))
       OR (id = 990310000000000008 AND (NOT (uid <=> @offerlab_demo_user_uid) OR NOT (question_id <=> 990200000000000008)))
       OR (uid = @offerlab_demo_user_uid AND question_id = 990200000000000001 AND id <> 990310000000000001)
       OR (uid = @offerlab_demo_user_uid AND question_id = 990200000000000002 AND id <> 990310000000000002)
       OR (uid = @offerlab_demo_user_uid AND question_id = 990200000000000003 AND id <> 990310000000000003)
       OR (uid = @offerlab_demo_user_uid AND question_id = 990200000000000004 AND id <> 990310000000000004)
       OR (uid = @offerlab_demo_user_uid AND question_id = 990200000000000005 AND id <> 990310000000000005)
       OR (uid = @offerlab_demo_user_uid AND question_id = 990200000000000006 AND id <> 990310000000000006)
       OR (uid = @offerlab_demo_user_uid AND question_id = 990200000000000007 AND id <> 990310000000000007)
       OR (uid = @offerlab_demo_user_uid AND question_id = 990200000000000008 AND id <> 990310000000000008)
) + (
    SELECT COUNT(*)
    FROM t_mock_interview_session
    WHERE id = 990400000000000001
      AND (
          NOT (uid <=> @offerlab_demo_user_uid)
          OR NOT (company <=> '深测科技')
          OR NOT (position <=> 'Java 后端')
      )
) + (
    SELECT COUNT(*)
    FROM t_mock_interview_answer
    WHERE (id = 990410000000000001 AND (
              NOT (uid <=> @offerlab_demo_user_uid)
              OR NOT (session_id <=> 990400000000000001)
              OR NOT (question_id <=> 990200000000000002)
          ))
       OR (id = 990410000000000002 AND (
              NOT (uid <=> @offerlab_demo_user_uid)
              OR NOT (session_id <=> 990400000000000001)
              OR NOT (question_id <=> 990200000000000004)
          ))
       OR (id = 990410000000000003 AND (
              NOT (uid <=> @offerlab_demo_user_uid)
              OR NOT (session_id <=> 990400000000000001)
              OR NOT (question_id <=> 990200000000000008)
          ))
       OR (session_id = 990400000000000001 AND question_id = 990200000000000002 AND id <> 990410000000000001)
       OR (session_id = 990400000000000001 AND question_id = 990200000000000004 AND id <> 990410000000000002)
       OR (session_id = 990400000000000001 AND question_id = 990200000000000008 AND id <> 990410000000000003)
);

INSERT INTO offerlab_demo_refresh_assertion (assertion_name, failure_count)
VALUES ('personalized_identity_preflight', @offerlab_demo_refresh_identity_conflicts);

SET @offerlab_demo_author_identity_conflicts := NULL;
SET @offerlab_demo_seed_asset_identity_conflicts := NULL;
SOURCE db/init/99_seed.sql;

SET @offerlab_demo_refresh_postcondition_failures := IF(
    COALESCE(@offerlab_demo_refresh_identity_conflicts, 1) = 0
        AND COALESCE(@offerlab_demo_author_identity_conflicts, 1) = 0
        AND COALESCE(@offerlab_demo_seed_asset_identity_conflicts, 1) = 0,
    0,
    1
) + IF(
    (
        SELECT COUNT(*)
        FROM t_user_account account
        WHERE account.id = @offerlab_demo_user_uid
          AND account.is_deleted = 0
          AND (
              (account.email = 'demo.author@offerlab.local' AND account.account_status = 2)
              OR (
                  account.email = 'demo.admin@offerlab.local'
                  AND account.account_status = 1
                  AND EXISTS (
                      SELECT 1
                      FROM t_user_admin admin
                      WHERE admin.uid = account.id
                        AND admin.role_code = 'ADMIN'
                        AND admin.enabled = 1
                  )
              )
          )
    ) = 1,
    0,
    1
) + IF(
    (
        SELECT COUNT(*)
        FROM t_user_prep_target
        WHERE id IN (990300000000000001, 990300000000000002, 990300000000000003)
          AND uid = @offerlab_demo_user_uid
    ) = 3,
    0,
    1
) + IF(
    (
        SELECT COUNT(*)
        FROM t_user_question_progress
        WHERE id BETWEEN 990310000000000001 AND 990310000000000008
          AND uid = @offerlab_demo_user_uid
    ) = 8,
    0,
    1
) + IF(
    (
        SELECT COUNT(*)
        FROM t_mock_interview_session
        WHERE id = 990400000000000001
          AND uid = @offerlab_demo_user_uid
    ) = 1,
    0,
    1
) + IF(
    (
        SELECT COUNT(*)
        FROM t_mock_interview_answer
        WHERE id IN (990410000000000001, 990410000000000002, 990410000000000003)
          AND session_id = 990400000000000001
          AND uid = @offerlab_demo_user_uid
    ) = 3,
    0,
    1
) + IF(
    (
        SELECT COUNT(*)
        FROM t_user_profile profile
        WHERE profile.id = @offerlab_demo_user_uid
          AND profile.is_deleted = 0
    ) = 1,
    0,
    1
) + IF(
    (
        SELECT COUNT(*)
        FROM t_tag
        WHERE (id BETWEEN 1001 AND 1010
               OR id BETWEEN 2001 AND 2007
               OR id BETWEEN 3001 AND 3005
               OR id BETWEEN 991000000000000001 AND 991000000000000010)
          AND is_deleted = 0
          AND tag_status = 1
    ) = 32,
    0,
    1
) + IF(
    (
        SELECT COUNT(*)
        FROM t_community_topic
        WHERE (id BETWEEN 990500000000000001 AND 990500000000000004
               OR id BETWEEN 991500000000000001 AND 991500000000000005)
          AND is_deleted = 0
          AND topic_status = 1
    ) = 9,
    0,
    1
) + IF(
    (
        SELECT COUNT(*)
        FROM t_community_topic_tag
        WHERE id BETWEEN 990510000000000001 AND 990510000000000008
           OR id BETWEEN 991510000000000001 AND 991510000000000010
    ) = 18,
    0,
    1
) + IF(
    (
        SELECT COUNT(*)
        FROM t_company_alias
        WHERE id BETWEEN 990010000000000001 AND 990010000000000008
          AND status = 1
    ) = 8,
    0,
    1
) + IF(
    (
        SELECT COUNT(*)
        FROM t_post_main
        WHERE (id BETWEEN 990100000000000001 AND 990100000000000006
               OR id BETWEEN 991100000000000001 AND 991100000000000015)
          AND author_id = @offerlab_demo_user_uid
          AND post_status = 1
          AND is_deleted = 0
    ) = 21,
    0,
    1
) + IF(
    (
        SELECT COUNT(*)
        FROM t_post_extension
        WHERE post_id BETWEEN 990100000000000001 AND 990100000000000006
           OR post_id BETWEEN 991100000000000001 AND 991100000000000015
    ) = 21,
    0,
    1
) + IF(
    (
        SELECT COUNT(*)
        FROM t_post_counter
        WHERE post_id BETWEEN 990100000000000001 AND 990100000000000006
           OR post_id BETWEEN 991100000000000001 AND 991100000000000015
    ) = 21,
    0,
    1
) + IF(
    (
        SELECT COUNT(*)
        FROM t_post_tag_ref
        WHERE id BETWEEN 990110000000000001 AND 990110000000000032
           OR id BETWEEN 991110000000000001 AND 991110000000000030
    ) = 62,
    0,
    1
) + IF(
    (
        SELECT COUNT(*)
        FROM t_interview_question
        WHERE id BETWEEN 990200000000000001 AND 990200000000000008
          AND source_author_uid = @offerlab_demo_user_uid
          AND status = 1
    ) = 8,
    0,
    1
) + IF(
    (
        SELECT COUNT(*)
        FROM t_interview_question_tag
        WHERE id BETWEEN 990210000000000001 AND 990210000000000016
    ) = 16,
    0,
    1
) + IF(
    @community_demo_uid <=> @offerlab_demo_user_uid,
    0,
    1
);

DELETE FROM offerlab_demo_refresh_assertion;
INSERT INTO offerlab_demo_refresh_assertion (assertion_name, failure_count)
VALUES ('refresh_postconditions', @offerlab_demo_refresh_postcondition_failures);

COMMIT;
DROP TEMPORARY TABLE IF EXISTS offerlab_demo_refresh_assertion;
SET SESSION autocommit = @offerlab_demo_refresh_original_autocommit;

SELECT
    id AS demo_retest_uid,
    email AS demo_retest_email,
    account_status AS demo_retest_account_status
FROM t_user_account
WHERE id = @offerlab_demo_user_uid;

SET @offerlab_demo_user_uid := NULL;
SET @offerlab_demo_refresh_original_autocommit := NULL;
