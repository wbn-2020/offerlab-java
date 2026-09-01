-- OfferLab R9 acceptance dataset rollback. Test environment only.
SET NAMES utf8mb4;
SET @batch_code = 'R9-20260817-ACCEPTANCE';

START TRANSACTION;

UPDATE t_tag tag
JOIN t_acceptance_data_batch_item audit
  ON audit.batch_code = @batch_code
 AND audit.entity_type = 'TAG_COUNTER'
 AND audit.entity_id = tag.id
SET tag.use_count = CAST(JSON_UNQUOTE(JSON_EXTRACT(audit.before_json, '$.useCount')) AS UNSIGNED);

DELETE item
FROM t_operation_slot_item item
JOIN t_acceptance_data_batch_item audit
  ON audit.batch_code = @batch_code
 AND audit.entity_type = 'OPERATION_SLOT_ITEM'
 AND audit.entity_id = item.id;

DELETE slot
FROM t_operation_slot slot
JOIN t_acceptance_data_batch_item audit
  ON audit.batch_code = @batch_code
 AND audit.entity_type = 'OPERATION_SLOT'
 AND audit.entity_id = slot.id;

DELETE tag_ref
FROM t_interview_question_tag tag_ref
JOIN t_acceptance_data_batch_item audit
  ON audit.batch_code = @batch_code
 AND audit.entity_type = 'QUESTION_TAG'
 AND audit.entity_id = tag_ref.id;

DELETE question
FROM t_interview_question question
JOIN t_acceptance_data_batch_item audit
  ON audit.batch_code = @batch_code
 AND audit.entity_type = 'QUESTION'
 AND audit.entity_id = question.id;

DELETE comment
FROM t_int_comment comment
JOIN t_acceptance_data_batch_item audit
  ON audit.batch_code = @batch_code
 AND audit.entity_type = 'COMMENT'
 AND audit.entity_id = comment.id;

DELETE tag_ref
FROM t_post_tag_ref tag_ref
JOIN t_acceptance_data_batch_item audit
  ON audit.batch_code = @batch_code
 AND audit.entity_type = 'POST_TAG'
 AND audit.entity_id = tag_ref.id;

DELETE counter
FROM t_post_counter counter
JOIN t_acceptance_data_batch_item audit
  ON audit.batch_code = @batch_code
 AND audit.entity_type = 'POST'
 AND audit.entity_id = counter.post_id;

DELETE extension
FROM t_post_extension extension
JOIN t_acceptance_data_batch_item audit
  ON audit.batch_code = @batch_code
 AND audit.entity_type = 'POST'
 AND audit.entity_id = extension.post_id;

DELETE post
FROM t_post_main post
JOIN t_acceptance_data_batch_item audit
  ON audit.batch_code = @batch_code
 AND audit.entity_type = 'POST'
 AND audit.entity_id = post.id;

DELETE counter
FROM t_user_counter counter
JOIN t_acceptance_data_batch_item audit
  ON audit.batch_code = @batch_code
 AND audit.entity_type = 'USER'
 AND audit.entity_id = counter.user_id;

DELETE profile
FROM t_user_profile profile
JOIN t_acceptance_data_batch_item audit
  ON audit.batch_code = @batch_code
 AND audit.entity_type = 'USER'
 AND audit.entity_id = profile.id;

DELETE account
FROM t_user_account account
JOIN t_acceptance_data_batch_item audit
  ON audit.batch_code = @batch_code
 AND audit.entity_type = 'USER'
 AND audit.entity_id = account.id;

DELETE FROM t_acceptance_data_batch_item
WHERE batch_code = @batch_code;

COMMIT;
