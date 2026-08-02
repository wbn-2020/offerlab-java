-- V28: opt-in public creator challenges and non-payment contribution badges.
-- No points, money, membership, ranking, certification, or automated rewards are stored here.
SET NAMES utf8mb4;

CREATE TABLE t_creator_growth_challenge (
    id                   BIGINT       NOT NULL PRIMARY KEY,
    challenge_code       VARCHAR(64)  NOT NULL,
    title                VARCHAR(80)  NOT NULL,
    description          VARCHAR(500) NOT NULL,
    domain               INT          NULL,
    post_type            INT          NULL,
    assist_template_code VARCHAR(64)  NULL,
    status               VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    starts_at            DATETIME(3)  NOT NULL,
    ends_at              DATETIME(3)  NOT NULL,
    operator_uid         BIGINT       NOT NULL,
    create_time          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_creator_growth_challenge_code (challenge_code),
    KEY idx_creator_growth_challenge_visible (status, starts_at, ends_at, id),
    CONSTRAINT chk_creator_growth_challenge_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'OFFLINE')),
    CONSTRAINT chk_creator_growth_challenge_window
        CHECK (ends_at > starts_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Opt-in public creator challenge definitions';

CREATE TABLE t_creator_growth_challenge_participation (
    id                  BIGINT      NOT NULL PRIMARY KEY,
    challenge_id        BIGINT      NOT NULL,
    uid                 BIGINT      NOT NULL,
    participation_status VARCHAR(16) NOT NULL DEFAULT 'JOINED',
    completed_post_id   BIGINT      NULL,
    joined_at           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    completed_at        DATETIME(3) NULL,
    create_time         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_creator_growth_challenge_participation (challenge_id, uid),
    KEY idx_creator_growth_participation_user (uid, participation_status, update_time, id),
    KEY idx_creator_growth_participation_completed_post (completed_post_id),
    CONSTRAINT chk_creator_growth_participation_status
        CHECK (participation_status IN ('JOINED', 'COMPLETED', 'WITHDRAWN')),
    CONSTRAINT chk_creator_growth_participation_completed
        CHECK (
            (participation_status = 'COMPLETED' AND completed_post_id IS NOT NULL AND completed_at IS NOT NULL)
            OR (participation_status IN ('JOINED', 'WITHDRAWN') AND completed_post_id IS NULL AND completed_at IS NULL)
        )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Creator opt-in challenge participation';

CREATE TABLE t_creator_growth_badge (
    id                                  BIGINT       NOT NULL PRIMARY KEY,
    badge_code                          VARCHAR(64)  NOT NULL,
    title                               VARCHAR(80)  NOT NULL,
    description                         VARCHAR(300) NOT NULL,
    required_completed_challenge_count  INT          NOT NULL,
    enabled                             TINYINT      NOT NULL DEFAULT 1,
    create_time                         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time                         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_creator_growth_badge_code (badge_code),
    CONSTRAINT chk_creator_growth_badge_requirement
        CHECK (required_completed_challenge_count > 0),
    CONSTRAINT chk_creator_growth_badge_enabled
        CHECK (enabled IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Non-payment creator contribution badge definitions';

CREATE TABLE t_creator_growth_badge_award (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    uid                 BIGINT       NOT NULL,
    badge_id            BIGINT       NOT NULL,
    source_challenge_id BIGINT       NOT NULL,
    award_status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    awarded_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_time         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_creator_growth_badge_award (uid, badge_id),
    KEY idx_creator_growth_badge_award_user (uid, awarded_at, id),
    CONSTRAINT chk_creator_growth_badge_award_status
        CHECK (award_status = 'ACTIVE')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Non-payment creator contribution badge awards';

INSERT INTO t_creator_growth_badge (
    id, badge_code, title, description, required_completed_challenge_count, enabled
) VALUES
    (202608020000001, 'PUBLIC_CREATOR_STARTER', '公开创作起步', '完成一次自愿加入的公开创作挑战。', 1, 1),
    (202608020000002, 'PUBLIC_CREATOR_STEADY', '持续公共创作', '完成三个不同的自愿公开创作挑战。', 3, 1);
