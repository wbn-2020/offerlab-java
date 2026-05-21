-- 99_seed.sql
-- 演示种子数据：标签库
SET NAMES utf8mb4;
USE offerlab;

INSERT INTO t_tag (id, tag_name, tag_type, is_official) VALUES
    (1001, 'Java', 1, 1),
    (1002, 'Go', 1, 1),
    (1003, 'Python', 1, 1),
    (1004, 'Spring', 1, 1),
    (1005, 'MySQL', 1, 1),
    (1006, 'Redis', 1, 1),
    (1007, 'Kafka', 1, 1),
    (1008, 'Elasticsearch', 1, 1),
    (1009, 'Netty', 1, 1),
    (1010, 'JVM', 1, 1),
    (2001, '字节跳动', 2, 1),
    (2002, '阿里巴巴', 2, 1),
    (2003, '腾讯', 2, 1),
    (2004, '美团', 2, 1),
    (2005, '小红书', 2, 1),
    (2006, '百度', 2, 1),
    (3001, 'Java 后端', 3, 1),
    (3002, 'Go 后端', 3, 1),
    (3003, '前端', 3, 1),
    (3004, '算法工程师', 3, 1)
ON DUPLICATE KEY UPDATE tag_name = VALUES(tag_name);
