CREATE TABLE IF NOT EXISTS t_creator_representative_post (
    id          BIGINT      NOT NULL PRIMARY KEY,
    creator_uid BIGINT     NOT NULL,
    post_id     BIGINT     NOT NULL,
    sort_order  INT        NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted  TINYINT    NOT NULL DEFAULT 0,
    UNIQUE KEY uk_creator_representative_post (creator_uid, post_id),
    KEY idx_creator_representative_active (creator_uid, is_deleted, sort_order, id),
    KEY idx_creator_representative_post (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Creator profile representative post links';
