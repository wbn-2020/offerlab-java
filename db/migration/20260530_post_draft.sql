SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS t_post_draft (
    id              BIGINT       NOT NULL PRIMARY KEY,
    uid             BIGINT       NOT NULL,
    source_post_id  BIGINT       NULL COMMENT '缂栬緫宸叉湁甯栧瓙鏃跺叧鑱旂殑甯栧瓙 ID',
    post_type       TINYINT      NOT NULL DEFAULT 1 COMMENT '1闈㈢粡 2鎶€鏈崥瀹?3棰樿В 4姹傝亴闂瓟',
    title           VARCHAR(255) NULL,
    content         LONGTEXT     NULL,
    cover_url       VARCHAR(512) NULL,
    visibility      TINYINT      NOT NULL DEFAULT 1,
    ext_json        JSON         NULL,
    tag_ids_json    JSON         NULL,
    tag_names_json  JSON         NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    KEY idx_uid_update_time (uid, update_time),
    KEY idx_uid_source_post (uid, source_post_id, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='甯栧瓙鏈嶅姟绔崏绋?;
