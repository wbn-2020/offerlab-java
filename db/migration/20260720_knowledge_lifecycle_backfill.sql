SET NAMES utf8mb4;

UPDATE t_int_content_suggestion
SET resolution = CASE decision
        WHEN 'ACCEPTED' THEN 'ACCEPTED'
        WHEN 'MERGED' THEN 'ACCEPTED'
        WHEN 'PARTIAL_ACCEPTED' THEN 'PARTIAL'
        WHEN 'REJECTED' THEN 'REJECTED'
        WHEN 'PLANNED' THEN 'PLANNED'
        ELSE resolution
    END,
    delivery_status = CASE
        WHEN result_version IS NOT NULL
             AND decision IN ('ACCEPTED', 'MERGED', 'PARTIAL_ACCEPTED', 'PLANNED')
            THEN 'LINKED'
        ELSE 'UNLINKED'
    END
WHERE decision IS NOT NULL;
