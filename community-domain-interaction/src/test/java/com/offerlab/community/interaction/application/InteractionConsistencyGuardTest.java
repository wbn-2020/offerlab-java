package com.offerlab.community.interaction.application;

import com.offerlab.community.interaction.api.enums.QuestionStatus;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractionConsistencyGuardTest {

    @Test
    void interactionCountersMustFollowActualStateTransitions() throws Exception {
        String service = read("src/main/java/com/offerlab/community/interaction/application/InteractionFacadeImpl.java");
        String likeMapper = read("src/main/java/com/offerlab/community/interaction/infrastructure/persistence/mapper/LikeMapper.java");
        String favoriteMapper = read("src/main/java/com/offerlab/community/interaction/infrastructure/persistence/mapper/FavoriteMapper.java");
        String commentMapper = read("src/main/java/com/offerlab/community/interaction/infrastructure/persistence/mapper/CommentMapper.java");
        String counterMapper = read("../community-domain-post/src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/PostCounterMapper.java");
        String initSql = read("../db/init/03_interaction.sql");
        String migration = read("../db/migration/20260530_relation_unique_keys.sql");
        String trustedMigration = read("../db/migration/20260713_trusted_content_stage1.sql");
        String trustedController = read("src/main/java/com/offerlab/community/interaction/controller/TrustedContentController.java");
        String trustedService = read("src/main/java/com/offerlab/community/interaction/application/TrustedContentService.java");
        String trustedDto = read("src/main/java/com/offerlab/community/interaction/api/dto/TrustedContentDTO.java");
        String trustStateMapper = read("src/main/java/com/offerlab/community/interaction/infrastructure/persistence/mapper/PostTrustStateMapper.java");
        String usefulMapper = read("src/main/java/com/offerlab/community/interaction/infrastructure/persistence/mapper/PostUsefulFeedbackMapper.java");
        String suggestionMapper = read("src/main/java/com/offerlab/community/interaction/infrastructure/persistence/mapper/ContentSuggestionMapper.java");
        String rateLimitAspect = read("../community-infrastructure/src/main/java/com/offerlab/community/infra/web/ratelimit/RateLimitAspect.java");

        assertTrue(service.contains("postFacade.getPost(postId, uid)"), "like/favorite must validate visibility for the caller");
        assertTrue(service.contains("postFacade.getPost(cmd.getPostId(), cmd.getAuthorUid())"), "comments must validate visibility for the author");
        assertTrue(service.contains("catch (DuplicateKeyException e)"), "unique-key races must map to business errors");
        assertTrue(service.contains("likeMapper.restoreById(existing.getId()) <= 0"), "like restore must check affected rows");
        assertTrue(service.contains("favoriteMapper.restoreToFolder(existing.getId(), folder.getId(), DEFAULT_SORT_ORDER) <= 0"), "favorite restore must check affected rows");
        assertTrue(service.contains("likeMapper.softDeleteById(po.getId()) <= 0"), "unlike must check affected rows");
        assertTrue(service.contains("favoriteMapper.softDeleteById(po.getId()) <= 0"), "unfavorite must check affected rows");
        assertTrue(service.contains("favoriteMapper.moveToFolder(favorite.getId(), uid, sourceFolderId, target.getId(), DEFAULT_SORT_ORDER) <= 0"), "favorite move must check affected rows");
        assertTrue(service.contains("int deletedCount = commentMapper.delete(deleteQuery)"), "comment deletion must use real affected rows");
        assertFalse(service.contains("Long deletedCount = commentMapper.selectCount(deleteQuery)"), "comment deletion must not count then delete");

        assertTrue(likeMapper.contains("AND is_deleted = 1"), "like restore must only restore deleted rows");
        assertTrue(favoriteMapper.contains("AND is_deleted = 1"), "favorite restore must only restore deleted rows");
        assertTrue(commentMapper.contains("GREATEST(0, like_count + #{delta})"), "comment like count must be atomic and non-negative");
        assertTrue(counterMapper.contains("GREATEST(0, like_count + #{delta})"), "post like count must not go negative");
        assertTrue(counterMapper.contains("GREATEST(0, comment_count + #{delta})"), "post comment count must not go negative");
        assertTrue(counterMapper.contains("GREATEST(0, favorite_count + #{delta})"), "post favorite count must not go negative");

        assertTrue(initSql.contains("UNIQUE KEY uk_user_target (user_id, target_type, target_id)"), "fresh like schema must use one relation row per target");
        assertTrue(initSql.contains("UNIQUE KEY uk_user_post (user_id, post_id)"), "fresh favorite schema must use one relation row per post");
        assertTrue(migration.contains("v20260530_rel_assert_no_duplicates"), "migration must block unsafe duplicate relation data");
        assertFalse(migration.toLowerCase().contains("delete "), "relation migration must not auto-delete historical rows");

        assertTrue(trustedMigration.contains("t_int_post_trust_state"), "trusted-content migration must persist question and freshness state");
        assertTrue(trustedMigration.contains("t_int_post_useful_feedback"), "trusted-content migration must persist one useful reason per user and post");
        assertTrue(trustedMigration.contains("t_int_content_suggestion"), "trusted-content migration must persist private suggestions and author decisions");
        assertTrue(trustedMigration.contains("UNIQUE KEY uk_user_post (user_id, post_id)"), "useful feedback must be unique per user and post");
        assertTrue(trustedMigration.contains("uk_pending_suggestion"), "pending duplicate suggestions must have a database guard");

        for (String status : new String[] {"OPEN", "ANSWERED", "ACCEPTED", "NO_RELIABLE_CONCLUSION", "CLOSED", "DUPLICATE"}) {
            assertTrue(trustedService.contains(status), "question lifecycle must support " + status);
        }
        Map<QuestionStatus, Set<QuestionStatus>> expectedQuestionTransitions = Map.of(
                QuestionStatus.OPEN, Set.of(
                        QuestionStatus.OPEN, QuestionStatus.ANSWERED,
                        QuestionStatus.NO_RELIABLE_CONCLUSION, QuestionStatus.CLOSED,
                        QuestionStatus.DUPLICATE),
                QuestionStatus.ANSWERED, Set.of(
                        QuestionStatus.ANSWERED, QuestionStatus.OPEN,
                        QuestionStatus.NO_RELIABLE_CONCLUSION, QuestionStatus.CLOSED,
                        QuestionStatus.DUPLICATE),
                QuestionStatus.ACCEPTED, Set.of(
                        QuestionStatus.ACCEPTED, QuestionStatus.OPEN, QuestionStatus.ANSWERED,
                        QuestionStatus.NO_RELIABLE_CONCLUSION, QuestionStatus.CLOSED,
                        QuestionStatus.DUPLICATE),
                QuestionStatus.NO_RELIABLE_CONCLUSION, Set.of(
                        QuestionStatus.NO_RELIABLE_CONCLUSION, QuestionStatus.OPEN,
                        QuestionStatus.ANSWERED, QuestionStatus.CLOSED, QuestionStatus.DUPLICATE),
                QuestionStatus.CLOSED, Set.of(
                        QuestionStatus.CLOSED, QuestionStatus.OPEN,
                        QuestionStatus.ANSWERED, QuestionStatus.DUPLICATE),
                QuestionStatus.DUPLICATE, Set.of(
                        QuestionStatus.DUPLICATE, QuestionStatus.OPEN,
                        QuestionStatus.ANSWERED, QuestionStatus.NO_RELIABLE_CONCLUSION,
                        QuestionStatus.CLOSED));
        for (QuestionStatus current : QuestionStatus.values()) {
            for (QuestionStatus target : QuestionStatus.values()) {
                assertEquals(
                        expectedQuestionTransitions.get(current).contains(target),
                        current.canTransitionTo(target),
                        () -> "unexpected question transition " + current + " -> " + target);
            }
        }
        for (String reason : new String[] {"SOLVED_PROBLEM", "SAVED_TIME", "HELPED_DECISION", "NEW_PERSPECTIVE", "WORTH_PRACTICING"}) {
            assertTrue(trustedService.contains(reason), "useful feedback must support " + reason);
        }
        for (String type : new String[] {"CORRECTION", "FRESHNESS_UPDATE", "CONDITIONS", "COUNTEREXAMPLE", "SOURCE", "FOLLOW_UP_RESULT"}) {
            assertTrue(trustedService.contains(type), "content suggestions must support " + type);
        }
        assertTrue(trustedService.contains("Post.TYPE_COMMUNITY_QUESTION"), "answer adoption must only apply to community question posts");
        assertTrue(trustedService.contains("comment.getParentId()"), "only root comments may be accepted as answers");
        assertTrue(trustedService.contains("post.getAuthorId().equals(actorUid)"), "question and suggestion decisions must enforce post ownership");
        assertTrue(trustedService.contains("post.getAuthorId().equals(userId)"), "authors must not mark their own posts useful or suggest corrections");
        assertTrue(trustedService.contains("lockState(post.getId(), QuestionStatus.OPEN.name())"),
                "all post types must create a trust-state row with a non-null internal question status");
        String saveUsefulFeedback = methodBody(
                trustedService,
                "public TrustedContentDTO saveUsefulFeedback",
                "public TrustedContentDTO clearUsefulFeedback");
        String clearUsefulFeedback = methodBody(
                trustedService,
                "public TrustedContentDTO clearUsefulFeedback",
                "public ContentSuggestionDTO submitSuggestion");
        assertTrue(trustedService.matches(
                        "(?s).*@Transactional\\(isolation = Isolation\\.READ_COMMITTED\\)\\s+"
                                + "public TrustedContentDTO saveUsefulFeedback.*"),
                "useful feedback writes must not inherit a repeatable-read snapshot created before the aggregate lock");
        assertTrue(trustedService.matches(
                        "(?s).*@Transactional\\(isolation = Isolation\\.READ_COMMITTED\\)\\s+"
                                + "public TrustedContentDTO clearUsefulFeedback.*"),
                "useful feedback clears must not inherit a repeatable-read snapshot created before the aggregate lock");
        assertTrue(saveUsefulFeedback.contains("lockState(post)")
                        && saveUsefulFeedback.indexOf("lockState(post)")
                        < saveUsefulFeedback.indexOf("usefulFeedbackMapper.selectByUserAndPostForUpdate"),
                "useful feedback writes must lock the post trust aggregate before locking and reading prior state");
        assertTrue(clearUsefulFeedback.contains("lockState(post)")
                        && clearUsefulFeedback.indexOf("lockState(post)")
                        < clearUsefulFeedback.indexOf("usefulFeedbackMapper.selectByUserAndPostForUpdate"),
                "useful feedback clears must lock the post trust aggregate before locking and reading prior state");
        assertFalse(trustedService.matches(
                        "(?s).*lockState\\(\\s*post\\.getId\\(\\),\\s*"
                                + "isCommunityQuestion\\(post\\) \\? QuestionStatus\\.OPEN\\.name\\(\\) : null\\s*\\).*"),
                "non-question posts must not insert a null into the non-null question_status column");
        String acceptAnswer = methodBody(
                trustedService,
                "public TrustedContentDTO acceptAnswer",
                "public TrustedContentDTO clearAcceptedAnswer");
        assertTrue(acceptAnswer.indexOf("commentMapper.selectByIdForUpdate")
                        < acceptAnswer.indexOf("PostTrustStatePO state = lockState(post)"),
                "answer adoption must preserve the comment-then-aggregate lock order");
        String foldComment = methodBody(service, "public void foldComment", "public void unfoldComment");
        String unfoldComment = methodBody(service, "public void unfoldComment", "public PageResult<PostBriefDTO> listLikedPosts");
        assertTrue(foldComment.contains("requireNormalCommentForUpdate")
                        && unfoldComment.contains("requireNormalCommentForUpdate"),
                "fold transitions must lock the comment row so answer adoption cannot race the fold signal");
        assertTrue(acceptAnswer.contains("canAcceptAnswerFrom(previous)"),
                "terminal question states must be reopened before accepting an answer");
        String updateQuestionState = methodBody(
                trustedService,
                "public TrustedContentDTO updateQuestionState",
                "public TrustedContentDTO acceptAnswer");
        assertTrue(updateQuestionState.contains("isQuestionStatusAllowed(previous, target, availableAnswers)"),
                "question writes must use the same answer-aware transition policy returned to clients");
        String allowedQuestionStatuses = methodBody(
                trustedService,
                "private List<QuestionStatus> allowedQuestionStatuses",
                "private static boolean isQuestionStatusAllowed");
        assertTrue(allowedQuestionStatuses.contains("commentMapper.countAvailableRootComments(postId)"),
                "allowed question statuses must account for currently available root answers");
        assertTrue(trustedService.contains("target != QuestionStatus.ACCEPTED")
                        && trustedService.contains("target != QuestionStatus.ANSWERED || availableAnswers > 0")
                        && trustedService.contains("target != QuestionStatus.OPEN || availableAnswers <= 0"),
                "the public transition list must reserve ACCEPTED for answer adoption and enforce answer counts");
        assertTrue(trustedDto.contains("List<QuestionStatus> allowedQuestionStatuses")
                        && trustedService.contains(".allowedQuestionStatuses("),
                "trusted-content responses must expose the server-authoritative question transition list");
        assertTrue(trustedService.contains("if (previous == FreshnessStatus.SUPERSEDED)"),
                "suggestion reducers must not revive superseded content");
        assertTrue(trustedService.contains("freshnessStatusAfterMergedSuggestions"),
                "merged freshness suggestions must keep awaiting confirmation while other items are pending");
        assertTrue(trustedService.contains("AWAITING_AUTHOR_CONFIRMATION"), "freshness suggestions must create an author confirmation task");
        assertTrue(trustedService.contains("MERGED"), "suggestions must support merge-through-edit decisions");
        String getSuggestion = methodBody(
                trustedService,
                "public ContentSuggestionDTO getSuggestion",
                "public List<ContentSuggestionDTO> listMySuggestions");
        assertTrue(getSuggestion.contains("suggestionMapper.selectById(suggestionId)"),
                "precise suggestion reads must resolve the persisted suggestion by id");
        assertTrue(getSuggestion.contains("postFacade.getPost(suggestion.getPostId(), viewerUid)"),
                "precise suggestion reads must re-check post visibility for the current viewer");
        assertTrue(getSuggestion.contains("Objects.equals(post.getAuthorId(), viewerUid)")
                        && getSuggestion.contains("Objects.equals(suggestion.getSubmitterUid(), viewerUid)"),
                "only the visible post author or suggestion submitter may read a suggestion");
        assertTrue(getSuggestion.indexOf("ErrorCode.RESOURCE_NOT_FOUND")
                        != getSuggestion.lastIndexOf("ErrorCode.RESOURCE_NOT_FOUND"),
                "missing, hidden, and unauthorized suggestions must share resource-not-found semantics");
        assertFalse(getSuggestion.contains("ErrorCode.FORBIDDEN")
                        || getSuggestion.contains("ErrorCode.POST_NOT_FOUND"),
                "precise suggestion reads must not disclose authorization or post visibility failures");

        assertTrue(trustedController.contains("@GetMapping(\"/{postId}/trusted-content\")"), "trusted content must expose a stable read endpoint");
        assertTrue(trustedController.contains("@PutMapping(\"/{postId}/accepted-answer\")"), "question authors must be able to accept an answer");
        assertTrue(trustedController.contains("@DeleteMapping(\"/{postId}/accepted-answer\")"), "question authors must be able to clear an accepted answer");
        assertTrue(trustedController.contains("@PostMapping(\"/{postId}/content-suggestions\")"), "readers must be able to submit private suggestions");
        assertTrue(trustedController.contains("@GetMapping(\"/content-suggestions/{suggestionId}\")"),
                "authorized participants must be able to read an existing suggestion by id");
        assertTrue(trustedController.contains("@PutMapping(\"/content-suggestions/{suggestionId}/decision\")"), "authors must be able to decide suggestions");
        assertTrue(rateLimitAspect.contains("ctx.setVariable(\"uid\", uid)"), "rate limiting must expose the authenticated uid as a safe SpEL variable");
        assertTrue(trustedController.contains("#uid"), "authenticated trusted-content rate limits must use the injected uid variable");
        assertFalse(trustedController.contains("T(com.offerlab.community.infra.security.UserContext).require()"),
                "rate-limit key resolution must not turn authentication failures into system errors");

        assertTrue(trustStateMapper.contains("FOR UPDATE"), "question and freshness state transitions must lock the aggregate row");
        assertTrue(usefulMapper.contains("ON DUPLICATE KEY UPDATE"), "useful feedback changes must be idempotent");
        assertTrue(usefulMapper.contains("selectByUserAndPostForUpdate")
                        && usefulMapper.contains("FOR UPDATE"),
                "useful feedback writes must read the latest user row under a database lock");
        assertTrue(suggestionMapper.contains("LIMIT #{limit}"), "suggestion lists must remain bounded");
        assertTrue(suggestionMapper.contains("AND public_note IS NOT NULL"),
                "private suggestion decisions without an explicit public note must stay private");
        assertTrue(suggestionMapper.contains("decision IN ('ACCEPTED', 'PARTIAL_ACCEPTED', 'MERGED')"),
                "rejected suggestions must not be projected as positive public updates");
        assertTrue(suggestionMapper.contains("selectPendingByContent")
                        && suggestionMapper.contains("normalized_content_hash = #{normalizedContentHash}"),
                "pending suggestion lookup must use the leading columns of the database uniqueness index");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String startSignature, String nextSignature) {
        int start = source.indexOf(startSignature);
        int end = source.indexOf(nextSignature, start + startSignature.length());
        return start < 0 || end < 0 ? "" : source.substring(start, end);
    }
}
