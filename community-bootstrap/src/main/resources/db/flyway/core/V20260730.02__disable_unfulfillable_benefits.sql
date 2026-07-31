-- Do not expose catalog entries whose entitlement has no consumer yet.
SET NAMES utf8mb4;

UPDATE t_virtual_benefit_catalog
SET enabled = 0,
    action_reason = 'Disabled until a real entitlement consumer is implemented',
    update_time = CURRENT_TIMESTAMP(3)
WHERE benefit_code NOT IN ('AI_ASSIST_QUOTA', 'COLLECTION_ORGANIZATION_QUOTA')
  AND enabled <> 0;
