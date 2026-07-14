-- Stage 2 public collaboration network.
-- Content needs, collaborative series, co-creation activities,
-- channel curation suggestions, and structured discussions.
SET NAMES utf8mb4;

ALTER TABLE t_community_topic
    ADD COLUMN domain TINYINT NULL
        COMMENT 'Primary community domain; NULL means a global topic'
        AFTER topic_type,
    ADD COLUMN allowed_domains VARCHAR(64) NULL
        COMMENT 'Comma-separated additional domain codes; NULL/empty with NULL domain means global'
        AFTER domain,
    ADD KEY idx_community_topic_domain (domain, topic_status, sort_order, id);

CREATE TABLE IF NOT EXISTS t_collab_content_need (
    id                  BIGINT        NOT NULL,
    creator_uid         BIGINT        NOT NULL,
    domain              TINYINT       NOT NULL,
    source_type         VARCHAR(32)   NOT NULL DEFAULT 'COMMUNITY',
    source_ref_id       BIGINT        NULL,
    content_format      VARCHAR(32)   NOT NULL DEFAULT 'ARTICLE',
    title               VARCHAR(120)  NOT NULL,
    description         VARCHAR(2000) NOT NULL,
    acceptance_criteria VARCHAR(1000) NULL,
    need_status         VARCHAR(24)   NOT NULL DEFAULT 'OPEN',
    claimed_by_uid      BIGINT        NULL,
    merged_into_need_id BIGINT        NULL,
    resolution_type     VARCHAR(24)   NULL COMMENT 'POST/QUESTION/SERIES',
    resolution_id       BIGINT        NULL,
    resolution_post_id  BIGINT        NULL,
    closed_reason       VARCHAR(500)  NULL,
    follower_count      INT           NOT NULL DEFAULT 0,
    risk_acknowledged   TINYINT       NOT NULL DEFAULT 0,
    moderation_hidden   TINYINT       NOT NULL DEFAULT 0,
    create_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_collab_need_public (moderation_hidden, need_status, domain, id),
    KEY idx_collab_need_creator (creator_uid, update_time, id),
    KEY idx_collab_need_claimed (claimed_by_uid, need_status, update_time, id),
    KEY idx_collab_need_source (source_type, source_ref_id),
    KEY idx_collab_need_merge_target (merged_into_need_id),
    KEY idx_collab_need_resolution (resolution_type, resolution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Public content needs';

CREATE TABLE IF NOT EXISTS t_collab_content_need_follow (
    id          BIGINT      NOT NULL,
    need_id     BIGINT      NOT NULL,
    uid         BIGINT      NOT NULL,
    active      TINYINT     NOT NULL DEFAULT 1,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_collab_need_follow (need_id, uid),
    KEY idx_collab_need_follow_uid (uid, active, update_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Content need follows';

CREATE TABLE IF NOT EXISTS t_collab_series (
    id            BIGINT        NOT NULL,
    owner_uid     BIGINT        NOT NULL,
    domain        TINYINT       NOT NULL,
    title         VARCHAR(120)  NOT NULL,
    description   VARCHAR(2000) NOT NULL,
    submission_instructions VARCHAR(1000) NULL,
    series_status VARCHAR(24)   NOT NULL DEFAULT 'OPEN',
    member_count  INT           NOT NULL DEFAULT 1,
    post_count    INT           NOT NULL DEFAULT 0,
    risk_acknowledged TINYINT   NOT NULL DEFAULT 0,
    moderation_hidden TINYINT   NOT NULL DEFAULT 0,
    create_time   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_collab_series_public (moderation_hidden, series_status, domain, id),
    KEY idx_collab_series_owner (owner_uid, update_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Collaborative content series';

CREATE TABLE IF NOT EXISTS t_collab_series_member (
    id          BIGINT      NOT NULL,
    series_id   BIGINT      NOT NULL,
    uid         BIGINT      NOT NULL,
    member_role VARCHAR(24) NOT NULL DEFAULT 'CONTRIBUTOR',
    member_status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    added_by    BIGINT      NOT NULL,
    exited_at   DATETIME(3) NULL,
    revoked_at  DATETIME(3) NULL,
    revoked_by  BIGINT      NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_collab_series_member (series_id, uid),
    KEY idx_collab_series_member_page (series_id, member_status, id),
    KEY idx_collab_series_member_uid (uid, member_status, update_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Collaborative series members';

CREATE TABLE IF NOT EXISTS t_collab_series_submission (
    id             BIGINT        NOT NULL,
    series_id      BIGINT        NOT NULL,
    post_id        BIGINT        NOT NULL,
    submitter_uid  BIGINT        NOT NULL,
    submission_note VARCHAR(1000) NULL,
    review_status  VARCHAR(24)   NOT NULL DEFAULT 'PENDING',
    reviewer_uid   BIGINT        NULL,
    review_note    VARCHAR(1000) NULL,
    reviewed_at    DATETIME(3)   NULL,
    create_time    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_collab_series_submission (series_id, post_id),
    KEY idx_collab_series_submission_review (series_id, review_status, id),
    KEY idx_collab_series_submission_submitter (submitter_uid, review_status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Collaborative series post submissions';

CREATE TABLE IF NOT EXISTS t_collab_series_contribution (
    id                   BIGINT      NOT NULL,
    series_id            BIGINT      NOT NULL,
    post_id              BIGINT      NOT NULL,
    contributor_uid      BIGINT      NOT NULL,
    contribution_type    VARCHAR(24) NOT NULL DEFAULT 'POST',
    source_submission_id BIGINT      NOT NULL,
    recorded_by          BIGINT      NOT NULL,
    create_time          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_collab_series_contribution_source (source_submission_id),
    KEY idx_collab_series_contribution_series (series_id, create_time, id),
    KEY idx_collab_series_contribution_user (contributor_uid, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable collaborative series contributions';

CREATE TABLE IF NOT EXISTS t_collab_activity (
    id              BIGINT        NOT NULL,
    owner_uid       BIGINT        NOT NULL,
    domain          TINYINT       NOT NULL,
    activity_type   VARCHAR(32)   NOT NULL DEFAULT 'OPEN_CALL',
    title           VARCHAR(120)  NOT NULL,
    description     VARCHAR(2000) NOT NULL,
    submission_rule VARCHAR(1000) NULL,
    activity_status VARCHAR(24)   NOT NULL DEFAULT 'DRAFT',
    result_summary  VARCHAR(4000) NULL,
    starts_at       DATETIME(3)   NULL,
    ends_at         DATETIME(3)   NULL,
    submission_count INT          NOT NULL DEFAULT 0,
    risk_acknowledged TINYINT     NOT NULL DEFAULT 0,
    moderation_hidden TINYINT     NOT NULL DEFAULT 0,
    create_time     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_collab_activity_public (moderation_hidden, activity_status, domain, id),
    KEY idx_collab_activity_owner (owner_uid, update_time, id),
    KEY idx_collab_activity_end (activity_status, ends_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Public co-creation activities';

CREATE TABLE IF NOT EXISTS t_collab_activity_submission (
    id              BIGINT        NOT NULL,
    activity_id     BIGINT        NOT NULL,
    post_id         BIGINT        NOT NULL,
    submitter_uid   BIGINT        NOT NULL,
    submission_note VARCHAR(1000) NULL,
    review_status   VARCHAR(24)   NOT NULL DEFAULT 'PENDING',
    reviewer_uid    BIGINT        NULL,
    review_note     VARCHAR(1000) NULL,
    reviewed_at     DATETIME(3)   NULL,
    result_type     VARCHAR(32)   NULL COMMENT 'TOPIC_POST/MAINTENANCE_TASK',
    result_id       BIGINT        NULL,
    result_status   VARCHAR(24)   NULL COMMENT 'COMPLETED/PENDING_EXECUTION/NOT_APPLICABLE',
    create_time     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_collab_activity_submission (activity_id, post_id),
    KEY idx_collab_activity_submission_review (activity_id, review_status, id),
    KEY idx_collab_activity_submission_submitter (submitter_uid, review_status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Co-creation activity submissions';

CREATE TABLE IF NOT EXISTS t_collab_curation_suggestion (
    id              BIGINT        NOT NULL,
    topic_id        BIGINT        NOT NULL,
    post_id         BIGINT        NOT NULL,
    submitter_uid   BIGINT        NOT NULL,
    domain          TINYINT       NOT NULL,
    rationale       VARCHAR(1000) NOT NULL,
    suggestion_type VARCHAR(24)   NOT NULL DEFAULT 'CONTENT',
    review_status   VARCHAR(24)   NOT NULL DEFAULT 'PENDING',
    reviewer_uid    BIGINT        NULL,
    review_note     VARCHAR(1000) NULL,
    reviewed_at     DATETIME(3)   NULL,
    result_type     VARCHAR(32)   NULL COMMENT 'TOPIC_POST/MAINTENANCE_TASK',
    result_id       BIGINT        NULL,
    result_status   VARCHAR(24)   NULL COMMENT 'COMPLETED/PENDING_EXECUTION/NOT_APPLICABLE',
    create_time     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    pending_guard   TINYINT GENERATED ALWAYS AS (
        CASE WHEN review_status = 'PENDING' THEN 1 ELSE NULL END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_collab_curation_pending (topic_id, post_id, submitter_uid, pending_guard),
    KEY idx_collab_curation_review (review_status, domain, id),
    KEY idx_collab_curation_topic (topic_id, review_status, id),
    KEY idx_collab_curation_submitter (submitter_uid, review_status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Community channel curation suggestions';

CREATE TABLE IF NOT EXISTS t_collab_topic_post (
    id              BIGINT       NOT NULL,
    topic_id        BIGINT       NOT NULL,
    post_id         BIGINT       NOT NULL,
    source_type     VARCHAR(24)  NOT NULL DEFAULT 'CURATION',
    source_id       BIGINT       NOT NULL,
    recorded_by     BIGINT       NOT NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_collab_topic_post (topic_id, post_id),
    UNIQUE KEY uk_collab_topic_post_source (source_type, source_id),
    KEY idx_collab_topic_post_post (post_id, topic_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Audited direct topic-post curation relation';

CREATE TABLE IF NOT EXISTS t_collab_office_hour (
    id                    BIGINT        NOT NULL,
    host_uid              BIGINT        NOT NULL,
    domain                TINYINT       NOT NULL,
    title                 VARCHAR(120)  NOT NULL,
    description           VARCHAR(2000) NOT NULL,
    topic_guidance        VARCHAR(1000) NULL,
    starts_at             DATETIME(3)   NOT NULL,
    ends_at               DATETIME(3)   NOT NULL,
    capacity              INT           NOT NULL,
    reserved_count        INT           NOT NULL DEFAULT 0 COMMENT 'Seats consumed by pending, accepted, or completed reservations',
    office_hour_status    VARCHAR(24)   NOT NULL DEFAULT 'DRAFT',
    risk_acknowledged     TINYINT       NOT NULL DEFAULT 0,
    moderation_hidden     TINYINT       NOT NULL DEFAULT 0,
    create_time           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_collab_office_hour_public (moderation_hidden, office_hour_status, domain, starts_at, id),
    KEY idx_collab_office_hour_host (host_uid, office_hour_status, starts_at, id),
    CONSTRAINT chk_collab_office_hour_capacity CHECK (
        capacity > 0 AND capacity <= 100
        AND reserved_count >= 0 AND reserved_count <= capacity
        AND ends_at > starts_at
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Public no-payment experience exchange office hours';

CREATE TABLE IF NOT EXISTS t_collab_office_hour_reservation (
    id                    BIGINT        NOT NULL,
    office_hour_id        BIGINT        NOT NULL,
    attendee_uid          BIGINT        NOT NULL,
    topic                 VARCHAR(160)  NOT NULL,
    context_detail        VARCHAR(1500) NULL,
    reservation_status    VARCHAR(24)   NOT NULL DEFAULT 'PENDING',
    response_note         VARCHAR(1000) NULL,
    decided_by            BIGINT        NULL,
    decided_at            DATETIME(3)   NULL,
    host_confirmed_at     DATETIME(3)   NULL,
    attendee_confirmed_at DATETIME(3)   NULL,
    completed_at          DATETIME(3)   NULL,
    cancelled_by          BIGINT        NULL,
    cancelled_at          DATETIME(3)   NULL,
    risk_acknowledged     TINYINT       NOT NULL DEFAULT 0,
    moderation_hidden     TINYINT       NOT NULL DEFAULT 0,
    create_time           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_collab_office_reservation_once (office_hour_id, attendee_uid),
    KEY idx_collab_office_reservation_host (office_hour_id, reservation_status, id),
    KEY idx_collab_office_reservation_attendee (attendee_uid, reservation_status, id),
    KEY idx_collab_office_reservation_expiry (reservation_status, office_hour_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Capacity-reserved office hour requests';

CREATE TABLE IF NOT EXISTS t_collab_office_hour_feedback (
    id              BIGINT        NOT NULL,
    reservation_id  BIGINT        NOT NULL,
    office_hour_id  BIGINT        NOT NULL,
    author_uid      BIGINT        NOT NULL,
    target_uid      BIGINT        NOT NULL,
    rating          TINYINT       NOT NULL,
    feedback_text   VARCHAR(1000) NULL,
    moderation_hidden TINYINT     NOT NULL DEFAULT 0,
    create_time     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_collab_office_feedback_author (reservation_id, author_uid),
    KEY idx_collab_office_feedback_target (target_uid, create_time, id),
    KEY idx_collab_office_feedback_hour (office_hour_id, moderation_hidden, id),
    CONSTRAINT chk_collab_office_feedback_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Mutual participant feedback after confirmed completion';

CREATE TABLE IF NOT EXISTS t_collab_discussion (
    id                BIGINT        NOT NULL,
    creator_uid       BIGINT        NOT NULL,
    source_post_id    BIGINT        NOT NULL,
    domain            TINYINT       NOT NULL,
    title             VARCHAR(120)  NOT NULL,
    prompt            VARCHAR(2000) NOT NULL,
    discussion_status VARCHAR(24)   NOT NULL DEFAULT 'OPEN',
    summary           VARCHAR(4000) NULL,
    consensus_state   VARCHAR(24)   NULL,
    author_follow_up  VARCHAR(2000) NULL,
    followed_up_by    BIGINT        NULL,
    followed_up_at    DATETIME(3)   NULL,
    summarized_by     BIGINT        NULL,
    summarized_at     DATETIME(3)   NULL,
    vote_count        INT           NOT NULL DEFAULT 0,
    risk_acknowledged TINYINT       NOT NULL DEFAULT 0,
    moderation_hidden TINYINT       NOT NULL DEFAULT 0,
    create_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_collab_discussion_public (moderation_hidden, discussion_status, domain, id),
    KEY idx_collab_discussion_post (source_post_id, moderation_hidden, id),
    KEY idx_collab_discussion_creator (creator_uid, update_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Structured public discussions';

CREATE TABLE IF NOT EXISTS t_collab_discussion_option (
    id            BIGINT       NOT NULL,
    discussion_id BIGINT       NOT NULL,
    option_text   VARCHAR(300) NOT NULL,
    sort_order    INT          NOT NULL DEFAULT 0,
    vote_count    INT          NOT NULL DEFAULT 0,
    create_time   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_collab_discussion_option (discussion_id, sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Structured discussion voting options';

CREATE TABLE IF NOT EXISTS t_collab_discussion_vote (
    id            BIGINT      NOT NULL,
    discussion_id BIGINT      NOT NULL,
    option_id     BIGINT      NOT NULL,
    uid           BIGINT      NOT NULL,
    create_time   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_collab_discussion_vote (discussion_id, uid),
    KEY idx_collab_discussion_vote_option (option_id, id),
    KEY idx_collab_discussion_vote_uid (uid, update_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='One structured discussion vote per user';

CREATE TABLE IF NOT EXISTS t_collab_governance_case (
    id              BIGINT        NOT NULL,
    case_type       VARCHAR(24)   NOT NULL COMMENT 'REPORT/APPEAL',
    target_type     VARCHAR(32)   NOT NULL,
    target_id       BIGINT        NOT NULL,
    submitter_uid   BIGINT        NOT NULL,
    parent_case_id  BIGINT        NULL,
    reason_code     VARCHAR(32)   NOT NULL,
    detail          VARCHAR(2000) NOT NULL,
    case_status     VARCHAR(24)   NOT NULL DEFAULT 'PENDING',
    reviewer_uid    BIGINT        NULL,
    review_note     VARCHAR(1000) NULL,
    reviewed_at     DATETIME(3)   NULL,
    create_time     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    pending_guard   TINYINT GENERATED ALWAYS AS (
        CASE WHEN case_status = 'PENDING' THEN 1 ELSE NULL END
    ) STORED,
    appeal_guard    BIGINT GENERATED ALWAYS AS (
        CASE WHEN case_type = 'APPEAL' THEN parent_case_id ELSE NULL END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_collab_case_pending (
        case_type, target_type, target_id, submitter_uid, pending_guard
    ),
    UNIQUE KEY uk_collab_case_single_appeal (appeal_guard),
    KEY idx_collab_case_review (case_status, id),
    KEY idx_collab_case_target (target_type, target_id, case_status, id),
    KEY idx_collab_case_submitter (submitter_uid, case_status, id),
    KEY idx_collab_case_daily_quota (submitter_uid, case_type, create_time, id),
    KEY idx_collab_case_parent (parent_case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Collaboration reports and appeals';
