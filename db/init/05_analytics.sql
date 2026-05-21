-- 05_analytics.sql
SET NAMES utf8mb4;
USE offerlab;

DROP TABLE IF EXISTS t_ana_extracted_question;
CREATE TABLE t_ana_extracted_question (
    id              BIGINT       NOT NULL PRIMARY KEY,
    source_post_id  BIGINT       NOT NULL,
    question_text   VARCHAR(2000) NOT NULL,
    answer_summary  TEXT         NULL,
    exam_points     JSON         NULL,
    difficulty      TINYINT      NULL,
    company         VARCHAR(64)  NULL,
    position        VARCHAR(64)  NULL,
    similarity_hash CHAR(64)     NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    KEY idx_source_post (source_post_id),
    KEY idx_company_position (company, position),
    KEY idx_simhash (similarity_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 提取的面试题';
