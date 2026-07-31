-- 03_interaction.sql
-- 互动域：评论、点赞、收藏
SET NAMES utf8mb4;

DROP TABLE IF EXISTS t_int_comment;
CREATE TABLE t_int_comment (
    id              BIGINT       NOT NULL PRIMARY KEY,
    post_id         BIGINT       NOT NULL,
    post_author_id  BIGINT       NOT NULL COMMENT '冗余：帖子作者',
    author_id       BIGINT       NOT NULL,
    root_id         BIGINT       NOT NULL DEFAULT 0,
    parent_id       BIGINT       NOT NULL DEFAULT 0,
    reply_to_uid    BIGINT       NULL,
    content         VARCHAR(2000) NOT NULL,
    like_count      INT          NOT NULL DEFAULT 0,
    helpful_count   INT          NOT NULL DEFAULT 0,
    comment_status  TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 2审核 3已删',
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    KEY idx_post_root (post_id, root_id, create_time),
    KEY idx_comment_quality_roots (post_id, root_id, comment_status, is_deleted, create_time, id),
    KEY idx_author_time (author_id, create_time),
    KEY idx_post_author_time (post_author_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论';

DROP TABLE IF EXISTS t_int_like;
CREATE TABLE t_int_like (
    id               BIGINT       NOT NULL PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    target_type      TINYINT      NOT NULL COMMENT '1帖子 2评论',
    target_id        BIGINT       NOT NULL,
    target_author_id BIGINT       NOT NULL,
    create_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    is_deleted       TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_user_target (user_id, target_type, target_id),
    KEY idx_like_user_page (user_id, target_type, is_deleted, create_time, id),
    KEY idx_target (target_type, target_id, create_time),
    KEY idx_target_author (target_author_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鐐硅禐';

DROP TABLE IF EXISTS t_int_favorite_folder;
CREATE TABLE t_int_favorite_folder (
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

DROP TABLE IF EXISTS t_int_favorite;
CREATE TABLE t_int_favorite (
    id              BIGINT       NOT NULL PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    post_id         BIGINT       NOT NULL,
    folder_id       BIGINT       NULL DEFAULT 0,
    sort_order      INT          NOT NULL DEFAULT 0,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_user_post (user_id, post_id),
    KEY idx_user_folder_sort (user_id, folder_id, sort_order, create_time),
    KEY idx_folder_time (folder_id, is_deleted, create_time),
    KEY idx_favorite_user_page (user_id, is_deleted, create_time, id),
    KEY idx_favorite_user_folder_page (user_id, folder_id, is_deleted, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鏀惰棌';

DROP TABLE IF EXISTS t_int_discussion_follow;
CREATE TABLE t_int_discussion_follow (
    id                        BIGINT      NOT NULL PRIMARY KEY,
    uid                       BIGINT      NOT NULL,
    post_id                   BIGINT      NOT NULL,
    follow_status             TINYINT     NOT NULL DEFAULT 1 COMMENT '1 following 0 unfollowed',
    last_read_comment_id      BIGINT      NULL,
    last_notified_comment_id  BIGINT      NULL,
    create_time               DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time               DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted                TINYINT     NOT NULL DEFAULT 0,
    UNIQUE KEY uk_discussion_follow_user_post (uid, post_id),
    KEY idx_discussion_follow_post_status (post_id, follow_status, id),
    KEY idx_discussion_follow_uid_status (uid, follow_status, update_time),
    KEY idx_discussion_follow_notify_page (post_id, follow_status, is_deleted, id),
    KEY idx_discussion_follow_uid_page (uid, follow_status, is_deleted, update_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='post discussion follows';

DROP TABLE IF EXISTS t_int_contact_request;
CREATE TABLE t_int_contact_request (
    id                    BIGINT       NOT NULL PRIMARY KEY,
    requester_uid         BIGINT       NOT NULL,
    receiver_uid          BIGINT       NOT NULL,
    source_type           VARCHAR(32)  NOT NULL,
    source_id             BIGINT       NULL,
    scene                 VARCHAR(32)  NOT NULL,
    message_preview       VARCHAR(500) NOT NULL,
    request_status        VARCHAR(32)  NOT NULL,
    receiver_action_time  DATETIME(3)  NULL,
    expire_time           DATETIME(3)  NULL,
    report_id             BIGINT       NULL,
    dedup_key             VARCHAR(128) NOT NULL,
    create_time           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted            TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_contact_request_dedup (dedup_key),
    KEY idx_contact_request_receiver_status (receiver_uid, request_status, update_time),
    KEY idx_contact_request_requester_status (requester_uid, request_status, update_time),
    KEY idx_contact_request_receiver_page (receiver_uid, is_deleted, request_status, update_time, id),
    KEY idx_contact_request_requester_page (requester_uid, is_deleted, request_status, update_time, id),
    KEY idx_contact_request_pair_status (requester_uid, receiver_uid, request_status, expire_time),
    KEY idx_contact_request_requester_day (requester_uid, is_deleted, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='contact requests';

DROP TABLE IF EXISTS t_int_comment_quality_signal;
CREATE TABLE t_int_comment_quality_signal (
    id              BIGINT       NOT NULL PRIMARY KEY,
    post_id         BIGINT       NOT NULL,
    comment_id      BIGINT       NOT NULL,
    root_id         BIGINT       NOT NULL DEFAULT 0,
    signal_type     VARCHAR(40)  NOT NULL COMMENT 'AUTHOR_PINNED / FEATURED / LOW_QUALITY_FOLDED',
    signal_status   TINYINT      NOT NULL DEFAULT 1 COMMENT '1 active 0 inactive',
    operator_uid    BIGINT       NOT NULL,
    operator_role   VARCHAR(32)  NOT NULL DEFAULT 'user',
    reason          VARCHAR(255) NULL,
    source          VARCHAR(32)  NOT NULL DEFAULT 'author',
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_comment_quality_signal_comment_type (comment_id, signal_type),
    KEY idx_comment_quality_signal_post_type_status (post_id, signal_type, signal_status, update_time),
    KEY idx_comment_quality_signal_root_type_status (root_id, signal_type, signal_status),
    KEY idx_comment_quality_post_comment (post_id, signal_type, signal_status, is_deleted, comment_id, root_id),
    KEY idx_comment_quality_signal_operator_time (operator_uid, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='comment quality signals';

DROP TABLE IF EXISTS t_int_comment_helpful;
CREATE TABLE t_int_comment_helpful (
    id              BIGINT      NOT NULL PRIMARY KEY,
    uid             BIGINT      NOT NULL,
    post_id         BIGINT      NOT NULL,
    comment_id      BIGINT      NOT NULL,
    helpful_status  TINYINT     NOT NULL DEFAULT 1 COMMENT '1 helpful 0 canceled',
    create_time     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted      TINYINT     NOT NULL DEFAULT 0,
    UNIQUE KEY uk_comment_helpful_uid_comment (uid, comment_id),
    KEY idx_comment_helpful_comment_status (comment_id, helpful_status),
    KEY idx_comment_helpful_user_status (uid, helpful_status, update_time),
    KEY idx_comment_helpful_post_comment (post_id, comment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='comment helpful marks';

DROP TABLE IF EXISTS t_int_post_trust_state;
CREATE TABLE t_int_post_trust_state (
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
    KEY idx_trust_state_successor_post (successor_post_id),
    CONSTRAINT fk_trust_state_post
        FOREIGN KEY (post_id) REFERENCES t_post_main (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_trust_state_accepted_comment
        FOREIGN KEY (accepted_comment_id) REFERENCES t_int_comment (id)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT fk_trust_state_duplicate_post
        FOREIGN KEY (duplicate_post_id) REFERENCES t_post_main (id)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT fk_trust_state_successor_post
        FOREIGN KEY (successor_post_id) REFERENCES t_post_main (id)
        ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Post question and freshness trust state';

DROP TABLE IF EXISTS t_int_post_useful_feedback;
CREATE TABLE t_int_post_useful_feedback (
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
    KEY idx_useful_feedback_author_time (post_author_id, create_time, post_id),
    CONSTRAINT fk_useful_feedback_post
        FOREIGN KEY (post_id) REFERENCES t_post_main (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_useful_feedback_user
        FOREIGN KEY (user_id) REFERENCES t_user_account (id)
        ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='One useful-content reason per user and post';

DROP TABLE IF EXISTS t_int_content_suggestion;
CREATE TABLE t_int_content_suggestion (
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
    KEY idx_content_suggestion_type_status (post_id, suggestion_type, decision),
    CONSTRAINT fk_content_suggestion_post
        FOREIGN KEY (post_id) REFERENCES t_post_main (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_content_suggestion_submitter
        FOREIGN KEY (submitter_uid) REFERENCES t_user_account (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_content_suggestion_decider
        FOREIGN KEY (post_author_id) REFERENCES t_user_account (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_content_suggestion_result_version
        FOREIGN KEY (post_id, result_version)
        REFERENCES t_post_version_history (post_id, result_version)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Private content corrections and author decisions';
