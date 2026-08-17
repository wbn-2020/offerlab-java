-- AUTHORIZED CONTROLLED ENVIRONMENT ONLY.
-- This one-time DDL must be separately reviewed and executed before the
-- materialized apply script. The apply script itself performs all of its
-- preflight checks before it starts a transaction or changes persistent data.

CREATE TABLE IF NOT EXISTS t_post_content_environment_backup (
    governance_batch_id VARCHAR(64) NOT NULL,
    post_id BIGINT NOT NULL,
    old_content_environment VARCHAR(16) NOT NULL,
    approved_content_environment VARCHAR(16) NOT NULL,
    backed_up_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (governance_batch_id, post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Exact old values for approved post environment governance';
