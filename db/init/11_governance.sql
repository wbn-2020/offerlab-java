-- 11_governance.sql
-- Audit log and lightweight content governance tables.
SET NAMES utf8mb4;
USE offerlab;

CREATE TABLE IF NOT EXISTS t_admin_audit_log (
    id              BIGINT        NOT NULL PRIMARY KEY,
    operator_uid    BIGINT        NULL,
    action          VARCHAR(64)   NOT NULL,
    resource_type   VARCHAR(64)   NOT NULL,
    resource_id     VARCHAR(64)   NULL,
    before_json     JSON          NULL,
    after_json      JSON          NULL,
    remark          VARCHAR(1000) NULL,
    create_time     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_operator_time (operator_uid, create_time),
    KEY idx_action_time (action, create_time),
    KEY idx_resource_time (resource_type, resource_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Admin audit log';

CREATE TABLE IF NOT EXISTS t_moderation_keyword (
    id              BIGINT       NOT NULL PRIMARY KEY,
    keyword         VARCHAR(128) NOT NULL,
    match_type      VARCHAR(16)  NOT NULL DEFAULT 'CONTAINS' COMMENT 'CONTAINS / EXACT',
    action          VARCHAR(16)  NOT NULL DEFAULT 'BLOCK' COMMENT 'BLOCK / REVIEW',
    scope           VARCHAR(32)  NOT NULL DEFAULT 'ALL' COMMENT 'ALL / POST / COMMENT / REPORT',
    enabled         TINYINT      NOT NULL DEFAULT 1,
    remark          VARCHAR(200) NOT NULL DEFAULT '',
    operator_uid    BIGINT       NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_keyword_scope (keyword, scope),
    KEY idx_scope_enabled (scope, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Moderation keywords';

CREATE TABLE IF NOT EXISTS t_moderation_keyword_hit (
    id              BIGINT        NOT NULL PRIMARY KEY,
    scope           VARCHAR(32)   NOT NULL DEFAULT 'ALL',
    uid             BIGINT        NULL,
    keyword_id      BIGINT        NULL,
    keyword         VARCHAR(128)  NOT NULL,
    action          VARCHAR(16)   NOT NULL DEFAULT 'BLOCK' COMMENT 'BLOCK / REVIEW',
    content_summary VARCHAR(200)  NOT NULL DEFAULT '',
    create_time     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_scope_time (scope, create_time),
    KEY idx_uid_time (uid, create_time),
    KEY idx_keyword_time (keyword, create_time),
    KEY idx_action_time (action, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Moderation keyword hit log';

CREATE TABLE IF NOT EXISTS t_user_moderation_state (
    uid             BIGINT       NOT NULL PRIMARY KEY,
    muted_until     DATETIME(3)  NULL,
    banned_until    DATETIME(3)  NULL,
    reason          VARCHAR(500) NOT NULL DEFAULT '',
    operator_uid    BIGINT       NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_muted_until (muted_until),
    KEY idx_banned_until (banned_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User moderation state';

CREATE TABLE IF NOT EXISTS t_review_queue (
    id              BIGINT        NOT NULL PRIMARY KEY,
    source_type     VARCHAR(32)   NOT NULL,
    source_id       BIGINT        NULL,
    title           VARCHAR(200)  NOT NULL,
    summary         VARCHAR(1000) NULL,
    risk_level      VARCHAR(16)   NOT NULL DEFAULT 'medium',
    queue_status    VARCHAR(16)   NOT NULL DEFAULT 'pending',
    assignee_uid    BIGINT        NULL,
    creator_uid     BIGINT        NULL,
    priority        INT           NOT NULL DEFAULT 0,
    due_time        DATETIME(3)   NULL,
    handled_time    DATETIME(3)   NULL,
    handle_result   VARCHAR(32)   NULL,
    handle_note     VARCHAR(500)  NULL,
    ext_json        JSON          NULL,
    create_time     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted      TINYINT       NOT NULL DEFAULT 0,
    UNIQUE KEY uk_review_queue_source (source_type, source_id),
    KEY idx_review_queue_status_priority (queue_status, priority, create_time),
    KEY idx_review_queue_source_status (source_type, queue_status),
    KEY idx_review_queue_assignee_status (assignee_uid, queue_status),
    KEY idx_review_queue_risk_status (risk_level, queue_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Unified review queue';

CREATE TABLE IF NOT EXISTS t_domain_config (
    domain              TINYINT      NOT NULL PRIMARY KEY COMMENT '1 tech, 2 career, 3 reading, 4 lifestyle, 5 investment',
    domain_name         VARCHAR(64)  NOT NULL,
    domain_slug         VARCHAR(32)  NOT NULL,
    description         VARCHAR(500) NULL,
    sort_order          INT          NOT NULL DEFAULT 0,
    enabled             TINYINT      NOT NULL DEFAULT 1,
    risk_level          VARCHAR(16)  NOT NULL DEFAULT 'LOW',
    posting_notice      VARCHAR(500) NULL,
    browse_notice       VARCHAR(500) NULL,
    interaction_notice  VARCHAR(500) NULL,
    created_by          BIGINT       NULL,
    updated_by          BIGINT       NULL,
    create_time         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_domain_config_slug (domain_slug),
    KEY idx_domain_config_enabled_sort (enabled, sort_order, domain),
    KEY idx_domain_config_risk_enabled (risk_level, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Community domain configuration';

INSERT INTO t_domain_config (
    domain, domain_name, domain_slug, description, sort_order, enabled, risk_level,
    posting_notice, browse_notice, interaction_notice, created_by, updated_by
) VALUES
    (1, '技术', 'tech', '技术实践、架构复盘、工程经验与工具资源。', 10, 1, 'LOW',
     '请尽量补充背景、方案与结果，方便后来者复用。',
     '公开技术内容默认可匿名浏览。', '点赞、收藏、评论前请先登录。', 0, 0),
    (2, '职场', 'career', '求职复盘、成长路径、协作经验与职业选择。', 20, 1, 'MEDIUM',
     '请避免泄露敏感个人与企业信息。', '部分内容可能匿名展示作者身份。', '参与互动前请先登录并遵守社区礼仪。', 0, 0),
    (3, '阅读', 'reading', '书单、方法论、长文摘录与学习笔记。', 30, 1, 'LOW',
     '鼓励给出你的理解、摘录边界与适用场景。',
     '公开阅读内容可直接浏览。', '登录后可收藏、评论与追踪后续讨论。', 0, 0),
    (4, '生活', 'lifestyle', '生活效率、习惯、平衡与个人体验。', 40, 1, 'LOW',
     '欢迎分享真实经验，避免医疗与法律断言。',
     '公开生活内容可直接浏览。', '互动前请先登录，保持友善表达。', 0, 0),
    (5, '投资理财', 'investment', '理财认知、风险教育与个人复盘。', 50, 1, 'HIGH',
     '该领域内容默认进入更严格审核，请避免收益承诺与荐股表述。',
     '浏览时请自行判断风险，社区不构成投资建议。', '互动前请先登录并遵守风险提示。', 0, 0)
ON DUPLICATE KEY UPDATE
    domain_name = VALUES(domain_name),
    domain_slug = VALUES(domain_slug),
    description = VALUES(description),
    sort_order = VALUES(sort_order),
    enabled = VALUES(enabled),
    risk_level = VALUES(risk_level),
    posting_notice = VALUES(posting_notice),
    browse_notice = VALUES(browse_notice),
    interaction_notice = VALUES(interaction_notice),
    updated_by = VALUES(updated_by);
