-- 20260712_unclassified_domain.sql
-- Keep missing or invalid legacy domains unclassified instead of assigning them to technology.
SET NAMES utf8mb4;

ALTER TABLE t_post_extension
    MODIFY COLUMN domain TINYINT
    GENERATED ALWAYS AS (
        CASE JSON_UNQUOTE(JSON_EXTRACT(ext_json, '$.domain'))
            WHEN '1' THEN 1
            WHEN '2' THEN 2
            WHEN '3' THEN 3
            WHEN '4' THEN 4
            WHEN '5' THEN 5
            ELSE NULL
        END
    ) VIRTUAL;
