-- Seed platform-wide post prohibitions required by PRODUCT.md.
-- The moderation model currently scopes by content type rather than domain, so
-- these explicit red-line terms are intentionally enforced for every post.
SET NAMES utf8mb4;

INSERT INTO t_moderation_keyword (
    id, keyword, match_type, action, scope, enabled, remark, operator_uid
) VALUES
    (2026073001001, '保本', 'CONTAINS', 'BLOCK', 'POST', 1, '平台禁止收益保证类表述', NULL),
    (2026073001002, '收益承诺', 'CONTAINS', 'BLOCK', 'POST', 1, '平台禁止收益保证类表述', NULL),
    (2026073001003, '荐股', 'CONTAINS', 'BLOCK', 'POST', 1, '平台禁止证券推荐类表述', NULL),
    (2026073001004, '代客理财', 'CONTAINS', 'BLOCK', 'POST', 1, '平台禁止代客决策类表述', NULL)
ON DUPLICATE KEY UPDATE
    match_type = VALUES(match_type),
    action = VALUES(action),
    enabled = VALUES(enabled),
    remark = VALUES(remark),
    update_time = CURRENT_TIMESTAMP(3);
