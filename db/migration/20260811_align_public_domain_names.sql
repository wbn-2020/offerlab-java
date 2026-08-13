-- 公开频道名称是用户在首页、发现页和发布器中反复看到的产品文案。
-- 统一已有环境和新建环境的展示口径，保留稳定的 domain 与 slug。
UPDATE t_domain_config
SET
    domain_name = CASE domain
        WHEN 1 THEN '科技数码'
        WHEN 2 THEN '职场经验'
        WHEN 3 THEN '学习成长'
        WHEN 4 THEN '生活方式'
        WHEN 5 THEN '投资理财'
        ELSE domain_name
    END,
    description = CASE domain
        WHEN 1 THEN '编程、AI 工具、产品体验、数码设备和效率工具。'
        WHEN 2 THEN '求职面试、实习转行、工作复盘和职场选择。'
        WHEN 3 THEN '学习方法、读书笔记、考试经验、技能提升和自我管理。'
        WHEN 4 THEN '租房、城市生活、消费经验、旅行、健康、情绪和日常。'
        WHEN 5 THEN '理财认知、风险教育与个人复盘。'
        ELSE description
    END
WHERE domain IN (1, 2, 3, 4, 5);
