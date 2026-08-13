-- Non-destructive public collaboration samples for the acceptance environment.
-- The data is deliberately limited to the demo migration location and is never
-- loaded by production's default Flyway configuration.
SET NAMES utf8mb4;

SET @collaboration_demo_owner_uid := COALESCE(
    (SELECT id FROM t_user_account WHERE email = 'demo.author@offerlab.local' AND is_deleted = 0 LIMIT 1),
    (SELECT id FROM t_user_account WHERE is_deleted = 0 ORDER BY id LIMIT 1)
);
SET @collaboration_acceptance_uid := COALESCE(
    (SELECT id FROM t_user_account WHERE email = 'user001@qq.com' AND is_deleted = 0 LIMIT 1),
    @collaboration_demo_owner_uid
);

SET @collaboration_seed_guard_sql := IF(
    @collaboration_demo_owner_uid IS NULL,
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''collaboration demo seed requires an active author''',
    'DO 0'
);
PREPARE collaboration_seed_guard_stmt FROM @collaboration_seed_guard_sql;
EXECUTE collaboration_seed_guard_stmt;
DEALLOCATE PREPARE collaboration_seed_guard_stmt;

INSERT INTO t_collab_content_need (
    id, creator_uid, domain, source_type, content_format, title, description,
    acceptance_criteria, need_status, follower_count, risk_acknowledged, moderation_hidden
) VALUES
    (
        991800000000000001, @collaboration_demo_owner_uid, 1, 'COMMUNITY', 'GUIDE',
        '补充一份家庭数据备份与恢复检查清单',
        '围绕家庭照片、重要文档和旧设备，整理可在普通电脑上执行的备份、恢复演练和故障记录步骤。',
        '包含准备清单、一次恢复演练和不适用边界。', 'OPEN', 0, 0, 0
    ),
    (
        991800000000000002, @collaboration_demo_owner_uid, 3, 'COMMUNITY', 'ARTICLE',
        '征集低成本学习系统的中断复盘案例',
        '收集真实学习计划中断后的调整案例，重点说明触发条件、已尝试做法和后续如何恢复。',
        '说明真实背景、调整过程和至少一条可复用提醒。', 'OPEN', 0, 0, 0
    )
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    description = VALUES(description),
    acceptance_criteria = VALUES(acceptance_criteria),
    need_status = VALUES(need_status),
    moderation_hidden = 0,
    update_time = CURRENT_TIMESTAMP(3);

INSERT INTO t_collab_series (
    id, owner_uid, domain, title, description, submission_instructions,
    series_status, member_count, post_count, risk_acknowledged, moderation_hidden
) VALUES
    (
        991810000000000001, @collaboration_demo_owner_uid, 1,
        '普通人的数字生活复原手册',
        '把备份、账号安全、旧设备利用和家庭资料管理的真实实践整理为可连续阅读的公开系列。',
        '投稿须为你本人发布的公开内容，并补充适用设备、成本和失败经验。',
        'OPEN', 2, 0, 0, 0
    ),
    (
        991810000000000002, @collaboration_demo_owner_uid, 3,
        '可持续学习的恢复力案例集',
        '沉淀读书、技能学习和自我管理中发生中断后重新建立节奏的真实过程。',
        '投稿须说明原计划、导致中断的条件、调整后的方案和限制。',
        'OPEN', 2, 0, 0, 0
    )
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    description = VALUES(description),
    submission_instructions = VALUES(submission_instructions),
    series_status = VALUES(series_status),
    moderation_hidden = 0,
    update_time = CURRENT_TIMESTAMP(3);

INSERT INTO t_collab_series_member (
    id, series_id, uid, member_role, member_status, added_by
) VALUES
    (991811000000000001, 991810000000000001, @collaboration_demo_owner_uid, 'OWNER', 'ACTIVE', @collaboration_demo_owner_uid),
    (991811000000000002, 991810000000000001, @collaboration_acceptance_uid, 'CONTRIBUTOR', 'ACTIVE', @collaboration_demo_owner_uid),
    (991811000000000003, 991810000000000002, @collaboration_demo_owner_uid, 'OWNER', 'ACTIVE', @collaboration_demo_owner_uid),
    (991811000000000004, 991810000000000002, @collaboration_acceptance_uid, 'CONTRIBUTOR', 'ACTIVE', @collaboration_demo_owner_uid)
ON DUPLICATE KEY UPDATE
    member_status = 'ACTIVE',
    added_by = VALUES(added_by),
    exited_at = NULL,
    revoked_at = NULL,
    revoked_by = NULL,
    update_time = CURRENT_TIMESTAMP(3);

INSERT INTO t_collab_activity (
    id, owner_uid, domain, activity_type, title, description, submission_rule,
    activity_status, starts_at, ends_at, submission_count, risk_acknowledged, moderation_hidden
) VALUES
    (
        991820000000000001, @collaboration_demo_owner_uid, 1, 'OPEN_CALL',
        '一周一次的数字整理小实验',
        '用一篇公开内容记录一次设备、账号、文件或工具整理实验，分享前后差异和失败环节。',
        '提交本人公开内容；说明操作对象、成本、结果和不建议照做的情况。',
        'OPEN', NOW(3) - INTERVAL 1 DAY, NOW(3) + INTERVAL 21 DAY, 0, 0, 0
    ),
    (
        991820000000000002, @collaboration_demo_owner_uid, 4, 'OPEN_CALL',
        '城市生活决策的真实账本',
        '征集租房、通勤、消费或日常安排中的小型决策复盘，帮助读者理解选择背后的限制。',
        '提交本人公开内容；必须包含背景、判断依据、结果和可迁移边界。',
        'OPEN', NOW(3) - INTERVAL 1 DAY, NOW(3) + INTERVAL 28 DAY, 0, 0, 0
    )
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    description = VALUES(description),
    submission_rule = VALUES(submission_rule),
    activity_status = VALUES(activity_status),
    starts_at = VALUES(starts_at),
    ends_at = VALUES(ends_at),
    moderation_hidden = 0,
    update_time = CURRENT_TIMESTAMP(3);

INSERT INTO t_collab_discussion (
    id, creator_uid, source_post_id, domain, title, prompt,
    discussion_status, vote_count, risk_acknowledged, moderation_hidden
) VALUES
    (
        991830000000000001, @collaboration_demo_owner_uid, 991100000000000002, 1,
        '开源工具的遥测功能，默认应如何设置？',
        '请结合实际使用场景讨论默认开启、默认关闭和首次明确选择三种做法分别会带来什么后果。',
        'OPEN', 0, 0, 0
    ),
    (
        991830000000000002, @collaboration_demo_owner_uid, 991100000000000010, 3,
        '学习计划中断后，先恢复节奏还是先复盘原因？',
        '请分享你认为更有效的第一步，并说明这个顺序在什么情况下可能不适用。',
        'OPEN', 0, 0, 0
    )
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    prompt = VALUES(prompt),
    discussion_status = VALUES(discussion_status),
    moderation_hidden = 0,
    update_time = CURRENT_TIMESTAMP(3);

INSERT INTO t_collab_discussion_option (
    id, discussion_id, option_text, sort_order, vote_count
) VALUES
    (991831000000000001, 991830000000000001, '默认关闭，由用户主动开启', 1, 0),
    (991831000000000002, 991830000000000001, '首次使用时明确选择', 2, 0),
    (991831000000000003, 991830000000000001, '默认开启，但提供易见的关闭入口', 3, 0),
    (991831000000000004, 991830000000000002, '先用最小动作恢复节奏', 1, 0),
    (991831000000000005, 991830000000000002, '先复盘中断原因再重排计划', 2, 0),
    (991831000000000006, 991830000000000002, '按当前精力决定先后顺序', 3, 0)
ON DUPLICATE KEY UPDATE
    option_text = VALUES(option_text),
    sort_order = VALUES(sort_order);

INSERT INTO t_collab_office_hour (
    id, host_uid, domain, title, description, topic_guidance, starts_at, ends_at,
    capacity, reserved_count, office_hour_status, risk_acknowledged, moderation_hidden
) VALUES
    (
        991840000000000001, @collaboration_demo_owner_uid, 1,
        '数字生活整理经验交流',
        '围绕家庭资料整理、设备迁移和账号安全分享真实做法，不提供付费咨询或技术服务。',
        '请在预约中说明设备条件、已尝试做法和希望交流的问题。',
        NOW(3) + INTERVAL 7 DAY, NOW(3) + INTERVAL 7 DAY + INTERVAL 90 MINUTE,
        8, 0, 'OPEN', 0, 0
    ),
    (
        991840000000000002, @collaboration_demo_owner_uid, 3,
        '学习系统恢复经验交流',
        '围绕计划中断、注意力管理和低压力复盘分享可验证的个人经验。',
        '请在预约中说明当前学习目标、卡点和已经尝试的方法。',
        NOW(3) + INTERVAL 10 DAY, NOW(3) + INTERVAL 10 DAY + INTERVAL 90 MINUTE,
        8, 0, 'OPEN', 0, 0
    )
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    description = VALUES(description),
    topic_guidance = VALUES(topic_guidance),
    starts_at = VALUES(starts_at),
    ends_at = VALUES(ends_at),
    office_hour_status = VALUES(office_hour_status),
    moderation_hidden = 0,
    update_time = CURRENT_TIMESTAMP(3);
