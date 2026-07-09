-- 11_governance.sql
-- Audit log and lightweight content governance tables.
SET NAMES utf8mb4;

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
    scope           VARCHAR(32)  NOT NULL DEFAULT 'ALL' COMMENT 'ALL / POST / COMMENT / REPORT / CONTACT_REQUEST / CONTENT_SERIES / PROFILE',
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
    source_type     VARCHAR(32)   NULL COMMENT 'POST / COMMENT / CONTACT_REQUEST',
    source_id       BIGINT        NULL,
    review_status   VARCHAR(16)   NULL COMMENT 'APPROVED / REJECTED / CLOSED',
    reviewer_uid    BIGINT        NULL,
    review_note     VARCHAR(1000) NULL,
    review_time     DATETIME(3)   NULL,
    create_time     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_scope_time (scope, create_time),
    KEY idx_uid_time (uid, create_time),
    KEY idx_keyword_time (keyword, create_time),
    KEY idx_action_time (action, create_time),
    KEY idx_source (source_type, source_id),
    KEY idx_review_status_time (review_status, review_time)
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
    (1, '鎶€鏈?, 'tech', '鎶€鏈疄璺点€佹灦鏋勫鐩樸€佸伐绋嬬粡楠屼笌宸ュ叿璧勬簮銆?, 10, 1, 'LOW',
     '璇峰敖閲忚ˉ鍏呰儗鏅€佹柟妗堜笌缁撴灉锛屾柟渚垮悗鏉ヨ€呭鐢ㄣ€?,
     '鍏紑鎶€鏈唴瀹归粯璁ゅ彲鍖垮悕娴忚銆?, '鐐硅禐銆佹敹钘忋€佽瘎璁哄墠璇峰厛鐧诲綍銆?, 0, 0),
    (2, '鑱屽満', 'career', '姹傝亴澶嶇洏銆佹垚闀胯矾寰勩€佸崗浣滅粡楠屼笌鑱屼笟閫夋嫨銆?, 20, 1, 'MEDIUM',
     '璇烽伩鍏嶆硠闇叉晱鎰熶釜浜轰笌浼佷笟淇℃伅銆?, '閮ㄥ垎鍐呭鍙兘鍖垮悕灞曠ず浣滆€呰韩浠姐€?, '鍙備笌浜掑姩鍓嶈鍏堢櫥褰曞苟閬靛畧绀惧尯绀间华銆?, 0, 0),
    (3, '闃呰', 'reading', '涔﹀崟銆佹柟娉曡銆侀暱鏂囨憳褰曚笌瀛︿範绗旇銆?, 30, 1, 'LOW',
     '榧撳姳缁欏嚭浣犵殑鐞嗚В銆佹憳褰曡竟鐣屼笌閫傜敤鍦烘櫙銆?,
     '鍏紑闃呰鍐呭鍙洿鎺ユ祻瑙堛€?, '鐧诲綍鍚庡彲鏀惰棌銆佽瘎璁轰笌杩借釜鍚庣画璁ㄨ銆?, 0, 0),
    (4, '鐢熸椿', 'lifestyle', '鐢熸椿鏁堢巼銆佷範鎯€佸钩琛′笌涓汉浣撻獙銆?, 40, 1, 'LOW',
     '娆㈣繋鍒嗕韩鐪熷疄缁忛獙锛岄伩鍏嶅尰鐤椾笌娉曞緥鏂█銆?,
     '鍏紑鐢熸椿鍐呭鍙洿鎺ユ祻瑙堛€?, '浜掑姩鍓嶈鍏堢櫥褰曪紝淇濇寔鍙嬪杽琛ㄨ揪銆?, 0, 0),
    (5, '鎶曡祫鐞嗚储', 'investment', '鐞嗚储璁ょ煡銆侀闄╂暀鑲蹭笌涓汉澶嶇洏銆?, 50, 1, 'HIGH',
     '璇ラ鍩熷唴瀹归粯璁よ繘鍏ユ洿涓ユ牸瀹℃牳锛岃閬垮厤鏀剁泭鎵胯涓庤崘鑲¤〃杩般€?,
     '娴忚鏃惰鑷鍒ゆ柇椋庨櫓锛岀ぞ鍖轰笉鏋勬垚鎶曡祫寤鸿銆?, '浜掑姩鍓嶈鍏堢櫥褰曞苟閬靛畧椋庨櫓鎻愮ず銆?, 0, 0)
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
