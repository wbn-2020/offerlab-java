package com.offerlab.community.interaction.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.mq.producer.EventPublisher;
import com.offerlab.community.interaction.api.dto.AcceptedAnswerCmd;
import com.offerlab.community.interaction.api.dto.ContentSuggestionDTO;
import com.offerlab.community.interaction.api.dto.ContentSuggestionDecisionCmd;
import com.offerlab.community.interaction.api.dto.ContentSuggestionSettingsCmd;
import com.offerlab.community.interaction.api.dto.ContentSuggestionSubmitCmd;
import com.offerlab.community.interaction.api.dto.FreshnessUpdateCmd;
import com.offerlab.community.interaction.api.dto.PublicContentSuggestionDTO;
import com.offerlab.community.interaction.api.dto.QuestionStateUpdateCmd;
import com.offerlab.community.interaction.api.dto.TrustedContentDTO;
import com.offerlab.community.interaction.api.dto.UsefulFeedbackCmd;
import com.offerlab.community.interaction.api.dto.UsefulFeedbackSummaryDTO;
import com.offerlab.community.interaction.api.enums.ContentSuggestionDecision;
import com.offerlab.community.interaction.api.enums.ContentSuggestionDeliveryStatus;
import com.offerlab.community.interaction.api.enums.ContentSuggestionResolution;
import com.offerlab.community.interaction.api.enums.ContentSuggestionStatus;
import com.offerlab.community.interaction.api.enums.ContentSuggestionTargetScope;
import com.offerlab.community.interaction.api.enums.ContentSuggestionType;
import com.offerlab.community.interaction.api.enums.FreshnessStatus;
import com.offerlab.community.interaction.api.enums.QuestionStatus;
import com.offerlab.community.interaction.api.enums.UsefulFeedbackReason;
import com.offerlab.community.interaction.api.event.AnswerAcceptedEvent;
import com.offerlab.community.interaction.api.event.AnswerAcceptanceInvalidatedEvent;
import com.offerlab.community.interaction.api.event.ContentSuggestionDecidedEvent;
import com.offerlab.community.interaction.api.event.ContentSuggestionSubmittedEvent;
import com.offerlab.community.interaction.api.event.PostFreshnessChangedEvent;
import com.offerlab.community.interaction.api.event.PostUsefulFeedbackChangedEvent;
import com.offerlab.community.interaction.api.event.QuestionStateChangedEvent;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.CommentMapper;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.CommentQualitySignalMapper;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.ContentSuggestionMapper;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.PostTrustStateMapper;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.PostUsefulFeedbackMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.CommentPO;
import com.offerlab.community.interaction.infrastructure.persistence.po.CommentQualitySignalPO;
import com.offerlab.community.interaction.infrastructure.persistence.po.ContentSuggestionPO;
import com.offerlab.community.interaction.infrastructure.persistence.po.PostTrustStatePO;
import com.offerlab.community.interaction.infrastructure.persistence.po.PostUsefulFeedbackPO;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.domain.repository.PostRepository;
import com.offerlab.community.user.api.UserFacade;
import com.offerlab.community.user.api.dto.UserBriefDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrustedContentService {

    private static final int COMMENT_STATUS_NORMAL = 1;
    private static final int MAX_SUGGESTION_LIST_LIMIT = 50;
    private static final int MAX_MERGED_SUGGESTIONS = 100;
    private static final int PUBLIC_SUGGESTION_LIMIT = 20;
    private static final int MAX_DETAIL_LENGTH = 2000;
    private static final int MAX_SOURCE_URL_LENGTH = 1000;
    private static final int MAX_TARGET_LOCATOR_LENGTH = 255;
    private static final int MAX_EXPECTED_CHANGE_LENGTH = 2000;
    private static final int MAX_AUTHOR_REPLY_LENGTH = 1000;
    private static final int MAX_PUBLIC_NOTE_LENGTH = 500;
    private static final String FOLDED_SIGNAL = "LOW_QUALITY_FOLDED";

    private static final Set<QuestionStatus> QUESTION_STATUSES = EnumSet.of(
            QuestionStatus.OPEN,
            QuestionStatus.ANSWERED,
            QuestionStatus.ACCEPTED,
            QuestionStatus.NO_RELIABLE_CONCLUSION,
            QuestionStatus.CLOSED,
            QuestionStatus.DUPLICATE);
    private static final Set<FreshnessStatus> FRESHNESS_STATUSES = EnumSet.of(
            FreshnessStatus.CURRENT,
            FreshnessStatus.POSSIBLY_STALE,
            FreshnessStatus.AWAITING_AUTHOR_CONFIRMATION,
            FreshnessStatus.UPDATED,
            FreshnessStatus.SUPERSEDED);
    private static final Set<UsefulFeedbackReason> USEFUL_REASONS = EnumSet.of(
            UsefulFeedbackReason.SOLVED_PROBLEM,
            UsefulFeedbackReason.SAVED_TIME,
            UsefulFeedbackReason.HELPED_DECISION,
            UsefulFeedbackReason.NEW_PERSPECTIVE,
            UsefulFeedbackReason.WORTH_PRACTICING);
    private static final Set<ContentSuggestionType> SUGGESTION_TYPES = EnumSet.of(
            ContentSuggestionType.CORRECTION,
            ContentSuggestionType.FRESHNESS_UPDATE,
            ContentSuggestionType.CONDITIONS,
            ContentSuggestionType.COUNTEREXAMPLE,
            ContentSuggestionType.SOURCE,
            ContentSuggestionType.FOLLOW_UP_RESULT);
    private static final Set<ContentSuggestionDecision> SUGGESTION_DECISIONS = EnumSet.of(
            ContentSuggestionDecision.ACCEPTED,
            ContentSuggestionDecision.PARTIAL_ACCEPTED,
            ContentSuggestionDecision.REJECTED,
            ContentSuggestionDecision.MERGED,
            ContentSuggestionDecision.PLANNED);

    private final PostTrustStateMapper trustStateMapper;
    private final PostUsefulFeedbackMapper usefulFeedbackMapper;
    private final ContentSuggestionMapper suggestionMapper;
    private final CommentMapper commentMapper;
    private final CommentQualitySignalMapper commentQualitySignalMapper;
    private final PostFacade postFacade;
    private final PostRepository postRepository;
    private final UserFacade userFacade;
    private final SnowflakeIdGenerator idGenerator;
    private final EventPublisher events;

    @Transactional(readOnly = true)
    public TrustedContentDTO getTrustedContent(Long postId, Long viewerUid) {
        PostDTO post = requireVisiblePost(postId, viewerUid);
        PostTrustStatePO state = trustStateMapper.selectByPostId(postId);
        return toTrustedContent(post, viewerUid, state == null ? defaultState(post) : state);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TrustedContentDTO saveUsefulFeedback(Long postId, Long userId, UsefulFeedbackCmd cmd) {
        requirePositive(userId);
        PostDTO post = requireVisiblePost(postId, userId);
        requireNotAuthor(post, userId);
        UsefulFeedbackReason reason = cmd == null ? null : cmd.getReason();
        if (reason == null || !USEFUL_REASONS.contains(reason)) {
            throw parameterError("unsupported useful feedback reason");
        }

        lockState(post);
        PostUsefulFeedbackPO previous = usefulFeedbackMapper.selectByUserAndPostForUpdate(userId, postId);
        usefulFeedbackMapper.upsert(
                previous == null ? idGenerator.nextId() : previous.getId(),
                userId,
                postId,
                post.getAuthorId(),
                reason.name());
        if (previous == null || !reason.name().equals(previous.getReason())) {
            publishUsefulFeedback(
                    post,
                    userId,
                    reason,
                    previous == null ? null : enumValue(UsefulFeedbackReason.class, previous.getReason()),
                    true);
        }
        return toTrustedContent(post, userId, currentState(post));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TrustedContentDTO clearUsefulFeedback(Long postId, Long userId) {
        requirePositive(userId);
        PostDTO post = requireVisiblePost(postId, userId);
        lockState(post);
        PostUsefulFeedbackPO previous = usefulFeedbackMapper.selectByUserAndPostForUpdate(userId, postId);
        if (usefulFeedbackMapper.deleteByUserAndPost(userId, postId) > 0 && previous != null) {
            publishUsefulFeedback(
                    post,
                    userId,
                    enumValue(UsefulFeedbackReason.class, previous.getReason()),
                    null,
                    false);
        }
        return toTrustedContent(post, userId, currentState(post));
    }

    @Transactional
    public ContentSuggestionDTO submitSuggestion(Long postId, Long userId, ContentSuggestionSubmitCmd cmd) {
        requirePositive(userId);
        PostDTO post = requireVisiblePost(postId, userId);
        requireNotAuthor(post, userId);
        ContentSuggestionType type = cmd == null ? null : cmd.getType();
        if (type == null || !SUGGESTION_TYPES.contains(type)) {
            throw parameterError("unsupported content suggestion type");
        }
        String detail = normalizeRequired(cmd.getDetail(), MAX_DETAIL_LENGTH, "suggestion detail");
        String sourceUrl = normalizeSourceUrl(cmd.getSourceUrl());
        String targetLocator = normalizeOptional(
                cmd.getTargetLocator(), MAX_TARGET_LOCATOR_LENGTH, "target locator");
        String expectedChange = normalizeOptional(
                cmd.getExpectedChange(), MAX_EXPECTED_CHANGE_LENGTH, "expected change");
        ContentSuggestionTargetScope targetScope = cmd.getTargetScope() == null
                ? ContentSuggestionTargetScope.OTHER
                : cmd.getTargetScope();
        Post currentPost = postRepository.findById(postId)
                .orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
        PostTrustStatePO state = lockState(post);
        if (!Objects.equals(state.getSuggestionsOpen(), 1)) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "content suggestions are closed");
        }

        String contentHash = sha256(normalizeForHash(detail));
        String pendingKey = sha256(userId + "|" + postId + "|" + type.name() + "|" + contentHash);
        ContentSuggestionPO existing = suggestionMapper.selectPendingByContent(
                postId, userId, type.name(), contentHash);
        if (existing != null) {
            markAwaitingAuthorConfirmation(post, state, userId, type);
            return toSuggestionDTO(existing, loadUsers(List.of(existing)));
        }

        ContentSuggestionPO suggestion = new ContentSuggestionPO();
        suggestion.setId(idGenerator.nextId());
        suggestion.setPostId(postId);
        suggestion.setPostAuthorId(post.getAuthorId());
        suggestion.setSubmitterUid(userId);
        suggestion.setSuggestionType(type.name());
        suggestion.setDetail(detail);
        suggestion.setNormalizedContentHash(contentHash);
        suggestion.setSourceUrl(sourceUrl);
        suggestion.setBaseVersion(currentPost.getVersion());
        suggestion.setTargetScope(targetScope.name());
        suggestion.setTargetLocator(targetLocator);
        suggestion.setExpectedChange(expectedChange == null ? detail : expectedChange);
        suggestion.setAllowPublicAttribution(Boolean.TRUE.equals(cmd.getAllowPublicAttribution()) ? 1 : 0);
        suggestion.setResolution(ContentSuggestionResolution.PENDING.name());
        suggestion.setDeliveryStatus(ContentSuggestionDeliveryStatus.UNLINKED.name());
        suggestion.setPendingDedupKey(pendingKey);
        try {
            suggestionMapper.insertSuggestion(suggestion);
        } catch (DuplicateKeyException e) {
            ContentSuggestionPO concurrent = suggestionMapper.selectPendingByContent(
                    postId, userId, type.name(), contentHash);
            if (concurrent != null) {
                markAwaitingAuthorConfirmation(post, state, userId, type);
                return toSuggestionDTO(concurrent, loadUsers(List.of(concurrent)));
            }
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        }

        markAwaitingAuthorConfirmation(post, state, userId, type);
        events.publish(ContentSuggestionSubmittedEvent.builder()
                .suggestionId(suggestion.getId())
                .postId(postId)
                .postAuthorUid(post.getAuthorId())
                .submitterUid(userId)
                .suggestionType(type.name())
                .timestamp(Instant.now().toEpochMilli())
                .build());
        return toSuggestionDTO(suggestion, loadUsers(List.of(suggestion)));
    }

    @Transactional(readOnly = true)
    public ContentSuggestionDTO getSuggestion(Long suggestionId, Long viewerUid) {
        requirePositive(suggestionId);
        requirePositive(viewerUid);
        ContentSuggestionPO suggestion = suggestionMapper.selectById(suggestionId);
        if (suggestion == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        PostDTO post = postFacade.getPost(suggestion.getPostId(), viewerUid);
        if (post == null
                || (!Objects.equals(post.getAuthorId(), viewerUid)
                && !Objects.equals(suggestion.getSubmitterUid(), viewerUid))) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return toSuggestionDTO(suggestion, loadUsers(List.of(suggestion)));
    }

    @Transactional(readOnly = true)
    public List<ContentSuggestionDTO> listMySuggestions(Long postId, Long userId,
                                                         ContentSuggestionStatus status, int limit) {
        requirePositive(userId);
        requireVisiblePost(postId, userId);
        List<ContentSuggestionPO> rows = suggestionMapper.listMine(
                postId, userId, status == null ? null : status.name(), clampSuggestionLimit(limit));
        Map<Long, UserBriefDTO> users = loadUsers(rows);
        return rows.stream().map(row -> toSuggestionDTO(row, users)).toList();
    }

    @Transactional(readOnly = true)
    public List<ContentSuggestionDTO> listAuthorSuggestions(Long postId, Long actorUid,
                                                            ContentSuggestionStatus status, int limit) {
        requirePositive(actorUid);
        PostDTO post = requireVisiblePost(postId, actorUid);
        requirePostAuthor(post, actorUid);
        List<ContentSuggestionPO> rows = suggestionMapper.listForAuthor(
                postId, status == null ? null : status.name(), clampSuggestionLimit(limit));
        Map<Long, UserBriefDTO> users = loadUsers(rows);
        return rows.stream().map(row -> toSuggestionDTO(row, users)).toList();
    }

    @Transactional
    public ContentSuggestionDTO decideSuggestion(Long suggestionId, Long actorUid,
                                                  ContentSuggestionDecisionCmd cmd) {
        requirePositive(suggestionId);
        requirePositive(actorUid);
        ContentSuggestionDecision decision = requestedDecision(cmd);
        ContentSuggestionResolution resolution = requestedResolution(cmd, decision);
        if (decision == null || !SUGGESTION_DECISIONS.contains(decision)
                || resolution == ContentSuggestionResolution.PENDING) {
            throw parameterError("unsupported content suggestion decision");
        }
        if (decision == ContentSuggestionDecision.MERGED) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                    "MERGED decisions must be linked by a post update");
        }

        ContentSuggestionPO snapshot = suggestionMapper.selectById(suggestionId);
        if (snapshot == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        PostDTO post = requireVisiblePost(snapshot.getPostId(), actorUid);
        requirePostAuthor(post, actorUid);
        PostTrustStatePO state = lockState(post);
        ContentSuggestionPO suggestion = suggestionMapper.selectByIdForUpdate(suggestionId);
        if (suggestion == null || !Objects.equals(suggestion.getPostId(), post.getId())) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!Objects.equals(suggestion.getPostAuthorId(), actorUid)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        String authorReply = normalizeOptional(cmd.getAuthorReply(), MAX_AUTHOR_REPLY_LENGTH, "author reply");
        String publicNote = normalizeOptional(cmd.getPublicNote(), MAX_PUBLIC_NOTE_LENGTH, "public note");
        ContentSuggestionResolution currentResolution = effectiveResolution(suggestion);
        if (currentResolution != ContentSuggestionResolution.PENDING) {
            if (resolution == currentResolution
                    && Objects.equals(authorReply, suggestion.getAuthorReply())
                    && Objects.equals(publicNote, suggestion.getPublicNote())) {
                return toSuggestionDTO(suggestion, loadUsers(List.of(suggestion)));
            }
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        if (suggestionMapper.resolve(
                suggestionId, decision.name(), resolution.name(), authorReply, publicNote) <= 0) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }

        suggestion.setDecision(decision.name());
        suggestion.setResolution(resolution.name());
        suggestion.setDeliveryStatus(ContentSuggestionDeliveryStatus.UNLINKED.name());
        suggestion.setAuthorReply(authorReply);
        suggestion.setPublicNote(publicNote);
        suggestion.setPendingDedupKey(null);
        suggestion.setDecidedAt(LocalDateTime.now());
        handleFreshnessSuggestionDecision(post, suggestion, actorUid, decision, state);
        publishSuggestionDecision(suggestion, actorUid, decision, null);
        return toSuggestionDTO(suggestion, loadUsers(List.of(suggestion)));
    }

    @Transactional
    public TrustedContentDTO updateSuggestionSettings(Long postId, Long actorUid,
                                                       ContentSuggestionSettingsCmd cmd) {
        requirePositive(actorUid);
        if (cmd == null || cmd.getSuggestionsOpen() == null) {
            throw parameterError("suggestionsOpen is required");
        }
        PostDTO post = requireVisiblePost(postId, actorUid);
        requirePostAuthor(post, actorUid);
        PostTrustStatePO state = lockState(post);
        int suggestionsOpen = Boolean.TRUE.equals(cmd.getSuggestionsOpen()) ? 1 : 0;
        if (Objects.equals(state.getSuggestionsOpen(), suggestionsOpen)) {
            return toTrustedContent(post, actorUid, state);
        }
        state.setSuggestionsOpen(suggestionsOpen);
        persistState(state);
        return toTrustedContent(post, actorUid, state);
    }

    @Transactional
    public TrustedContentDTO updateQuestionState(Long postId, Long actorUid, QuestionStateUpdateCmd cmd) {
        requirePositive(actorUid);
        PostDTO post = requireVisibleQuestion(postId, actorUid);
        requirePostAuthor(post, actorUid);
        QuestionStatus target = cmd == null ? null : cmd.getStatus();
        if (target == null || !QUESTION_STATUSES.contains(target)) {
            throw parameterError("unsupported question status");
        }
        if (target == QuestionStatus.ACCEPTED) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                    "use the accepted-answer endpoint to accept a response");
        }

        PostTrustStatePO state = lockState(post);
        QuestionStatus previous = questionStatus(state);
        long availableAnswers = commentMapper.countAvailableRootComments(postId);
        if (!isQuestionStatusAllowed(previous, target, availableAnswers)) {
            if (target == QuestionStatus.ANSWERED && availableAnswers <= 0) {
                throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "no valid root response exists");
            }
            if (target == QuestionStatus.OPEN && availableAnswers > 0) {
                throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "valid root responses require ANSWERED");
            }
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        Long duplicatePostId = null;
        if (target == QuestionStatus.DUPLICATE) {
            duplicatePostId = cmd.getDuplicatePostId();
            requirePublicQuestionTarget(postId, duplicatePostId);
        } else if (cmd.getDuplicatePostId() != null) {
            throw parameterError("duplicatePostId is only valid for DUPLICATE");
        }
        if (previous == target
                && state.getAcceptedCommentId() == null
                && Objects.equals(state.getDuplicatePostId(), duplicatePostId)) {
            return toTrustedContent(post, actorUid, state);
        }

        Long invalidatedCommentId = state.getAcceptedCommentId();
        state.setQuestionStatus(target.name());
        state.setAcceptedCommentId(null);
        state.setDuplicatePostId(duplicatePostId);
        persistState(state);
        publishAnswerInvalidated(postId, invalidatedCommentId, actorUid, "QUESTION_STATE_CHANGED");
        publishQuestionState(post, actorUid, previous, state);
        return toTrustedContent(post, actorUid, state);
    }

    @Transactional
    public TrustedContentDTO acceptAnswer(Long postId, Long actorUid, AcceptedAnswerCmd cmd) {
        requirePositive(actorUid);
        if (cmd == null || cmd.getCommentId() == null || cmd.getCommentId() <= 0) {
            throw parameterError("commentId is required");
        }
        PostDTO post = requireVisibleQuestion(postId, actorUid);
        requirePostAuthor(post, actorUid);
        CommentPO comment = commentMapper.selectByIdForUpdate(cmd.getCommentId());
        if (comment == null
                || !Objects.equals(comment.getPostId(), postId)
                || !Objects.equals(comment.getCommentStatus(), COMMENT_STATUS_NORMAL)
                || Objects.equals(comment.getIsDeleted(), 1)
                || (comment.getParentId() != null && comment.getParentId() > 0)
                || (comment.getRootId() != null && comment.getRootId() > 0)
                || isFolded(comment.getId())) {
            throw new BizException(ErrorCode.COMMENT_NOT_FOUND);
        }
        PostTrustStatePO state = lockState(post);
        if (QuestionStatus.ACCEPTED.name().equals(state.getQuestionStatus())
                && Objects.equals(state.getAcceptedCommentId(), comment.getId())) {
            return toTrustedContent(post, actorUid, state);
        }

        QuestionStatus previous = questionStatus(state);
        if (!canAcceptAnswerFrom(previous)) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                    "reopen the question before accepting an answer");
        }
        Long previousAcceptedCommentId = state.getAcceptedCommentId();
        state.setQuestionStatus(QuestionStatus.ACCEPTED.name());
        state.setAcceptedCommentId(comment.getId());
        state.setDuplicatePostId(null);
        persistState(state);
        if (previousAcceptedCommentId != null
                && !Objects.equals(previousAcceptedCommentId, comment.getId())) {
            publishAnswerInvalidated(postId, previousAcceptedCommentId, actorUid, "ANSWER_REPLACED");
        }
        publishQuestionState(post, actorUid, previous, state);
        Long acceptanceId = idGenerator.nextId();
        events.publish(AnswerAcceptedEvent.builder()
                .postId(postId)
                .postAuthorUid(post.getAuthorId())
                .commentId(comment.getId())
                .commentAuthorUid(comment.getAuthorId())
                .actorUid(actorUid)
                .acceptanceId(acceptanceId)
                .timestamp(Instant.now().toEpochMilli())
                .build());
        return toTrustedContent(post, actorUid, state);
    }

    @Transactional
    public TrustedContentDTO clearAcceptedAnswer(Long postId, Long actorUid) {
        requirePositive(actorUid);
        PostDTO post = requireVisibleQuestion(postId, actorUid);
        requirePostAuthor(post, actorUid);
        PostTrustStatePO state = lockState(post);
        if (state.getAcceptedCommentId() == null
                && !QuestionStatus.ACCEPTED.name().equals(state.getQuestionStatus())) {
            return toTrustedContent(post, actorUid, state);
        }
        QuestionStatus previous = questionStatus(state);
        Long invalidatedCommentId = state.getAcceptedCommentId();
        state.setAcceptedCommentId(null);
        state.setQuestionStatus(statusFromAvailableAnswers(postId).name());
        persistState(state);
        publishAnswerInvalidated(postId, invalidatedCommentId, actorUid, "ANSWER_ACCEPTANCE_CLEARED");
        publishQuestionState(post, actorUid, previous, state);
        return toTrustedContent(post, actorUid, state);
    }

    @Transactional
    public TrustedContentDTO updateFreshness(Long postId, Long actorUid, FreshnessUpdateCmd cmd) {
        requirePositive(actorUid);
        PostDTO post = requireVisiblePost(postId, actorUid);
        requirePostAuthor(post, actorUid);
        FreshnessStatus target = cmd == null ? null : cmd.getStatus();
        if (target == null || !FRESHNESS_STATUSES.contains(target)) {
            throw parameterError("unsupported freshness status");
        }
        Long successorPostId = cmd.getSuccessorPostId();
        if (target == FreshnessStatus.SUPERSEDED && successorPostId == null) {
            throw parameterError("SUPERSEDED requires a successor post");
        }
        if (target != FreshnessStatus.SUPERSEDED && successorPostId != null) {
            throw parameterError("successorPostId is only valid for SUPERSEDED");
        }
        if (target == FreshnessStatus.SUPERSEDED) {
            requirePublicSuccessor(postId, successorPostId);
        }

        PostTrustStatePO state = lockState(post);
        FreshnessStatus previous = freshnessStatus(state);
        if (previous == target && Objects.equals(state.getSuccessorPostId(), successorPostId)) {
            return toTrustedContent(post, actorUid, state);
        }
        state.setFreshnessStatus(target.name());
        state.setSuccessorPostId(successorPostId);
        if (target == FreshnessStatus.CURRENT
                || target == FreshnessStatus.UPDATED
                || target == FreshnessStatus.SUPERSEDED) {
            state.setLastConfirmedAt(LocalDateTime.now());
        }
        persistState(state);
        publishFreshnessState(post, actorUid, previous, state);
        return toTrustedContent(post, actorUid, state);
    }

    @Transactional
    public void onValidRootCommentCreated(PostDTO post, CommentPO comment) {
        if (!isCommunityQuestion(post)
                || comment == null
                || !Objects.equals(comment.getCommentStatus(), COMMENT_STATUS_NORMAL)
                || Objects.equals(comment.getIsDeleted(), 1)
                || (comment.getParentId() != null && comment.getParentId() > 0)
                || (comment.getRootId() != null && comment.getRootId() > 0)) {
            return;
        }
        PostTrustStatePO state = lockState(post);
        QuestionStatus previous = questionStatus(state);
        if (previous != QuestionStatus.OPEN) {
            return;
        }
        state.setQuestionStatus(QuestionStatus.ANSWERED.name());
        persistState(state);
        publishQuestionState(post, comment.getAuthorId(), previous, state);
    }

    @Transactional
    public void onRootCommentUnavailable(PostDTO post, Long rootCommentId, Long actorUid) {
        if (!isCommunityQuestion(post) || rootCommentId == null || rootCommentId <= 0) {
            return;
        }
        PostTrustStatePO state = lockState(post);
        QuestionStatus previous = questionStatus(state);
        boolean acceptedRemoved = Objects.equals(state.getAcceptedCommentId(), rootCommentId);
        if (acceptedRemoved) {
            state.setAcceptedCommentId(null);
            state.setQuestionStatus(statusFromAvailableAnswers(post.getId()).name());
        } else if (previous == QuestionStatus.ANSWERED
                && commentMapper.countAvailableRootComments(post.getId()) <= 0) {
            state.setQuestionStatus(QuestionStatus.OPEN.name());
        } else {
            return;
        }
        persistState(state);
        if (acceptedRemoved) {
            publishAnswerInvalidated(post.getId(), rootCommentId, actorUid, "ACCEPTED_ANSWER_UNAVAILABLE");
        }
        publishQuestionState(post, actorUid, previous, state);
    }

    @Transactional
    public void onRootCommentAvailable(PostDTO post, Long rootCommentId, Long actorUid) {
        if (!isCommunityQuestion(post) || rootCommentId == null || rootCommentId <= 0) {
            return;
        }
        PostTrustStatePO state = lockState(post);
        QuestionStatus previous = questionStatus(state);
        if (previous != QuestionStatus.OPEN
                || commentMapper.countAvailableRootComments(post.getId()) <= 0) {
            return;
        }
        state.setQuestionStatus(QuestionStatus.ANSWERED.name());
        persistState(state);
        publishQuestionState(post, actorUid, previous, state);
    }

    @Transactional
    public void mergeSuggestionsFromPostUpdate(Long postId, Long authorId,
                                               Collection<Long> respondedSuggestionIds,
                                               Integer resultVersion) {
        linkSuggestionsFromPostUpdate(postId, authorId, respondedSuggestionIds, resultVersion);
    }

    @Transactional
    public void linkSuggestionsFromPostUpdate(Long postId, Long authorId,
                                              Collection<Long> respondedSuggestionIds,
                                              Integer resultVersion) {
        if (postId == null || postId <= 0
                || authorId == null || authorId <= 0
                || resultVersion == null || resultVersion <= 0
                || respondedSuggestionIds == null || respondedSuggestionIds.isEmpty()) {
            return;
        }
        List<Long> ids = respondedSuggestionIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .limit(MAX_MERGED_SUGGESTIONS + 1L)
                .toList();
        if (ids.size() > MAX_MERGED_SUGGESTIONS) {
            throw parameterError("too many responded suggestion ids");
        }
        if (ids.isEmpty()) {
            return;
        }
        Post domainPost = postRepository.findById(postId)
                .orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
        if (!Objects.equals(domainPost.getAuthorId(), authorId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        PostDTO post = PostDTO.builder()
                .id(postId)
                .authorId(authorId)
                .postType(domainPost.getPostType())
                .visibility(domainPost.getVisibility())
                .postStatus(domainPost.getPostStatus())
                .build();
        PostTrustStatePO state = lockState(post);
        List<ContentSuggestionPO> suggestions = suggestionMapper.selectByIdsForUpdate(ids);
        if (suggestions.size() != ids.size()) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        boolean freshnessMerged = false;
        for (ContentSuggestionPO suggestion : suggestions) {
            if (!Objects.equals(suggestion.getPostId(), postId)) {
                throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            if (!Objects.equals(suggestion.getPostAuthorId(), authorId)) {
                throw new BizException(ErrorCode.FORBIDDEN);
            }
            ContentSuggestionResolution resolution = effectiveResolution(suggestion);
            ContentSuggestionDeliveryStatus deliveryStatus = effectiveDeliveryStatus(suggestion);
            if (deliveryStatus == ContentSuggestionDeliveryStatus.LINKED) {
                if (Objects.equals(suggestion.getResultVersion(), resultVersion)) {
                    continue;
                }
                throw new BizException(ErrorCode.INVALID_STATUS);
            }
            if (resolution == ContentSuggestionResolution.REJECTED
                    || (!resolution.canLinkToVersion()
                    && resolution != ContentSuggestionResolution.PENDING)) {
                throw new BizException(ErrorCode.INVALID_STATUS);
            }
            boolean firstLink = resolution == ContentSuggestionResolution.PENDING;
            ContentSuggestionResolution linkedResolution = firstLink
                    ? ContentSuggestionResolution.ACCEPTED
                    : resolution;
            ContentSuggestionDecision linkedDecision = firstLink
                    ? ContentSuggestionDecision.MERGED
                    : compatibleDecision(suggestion, linkedResolution);
            if (suggestionMapper.linkToVersion(
                    suggestion.getId(),
                    linkedDecision.name(),
                    linkedResolution.name(),
                    resultVersion) <= 0) {
                throw new BizException(ErrorCode.INVALID_STATUS);
            }
            suggestion.setDecision(linkedDecision.name());
            suggestion.setResolution(linkedResolution.name());
            suggestion.setDeliveryStatus(ContentSuggestionDeliveryStatus.LINKED.name());
            suggestion.setResultVersion(resultVersion);
            suggestion.setPendingDedupKey(null);
            if (suggestion.getDecidedAt() == null) {
                suggestion.setDecidedAt(LocalDateTime.now());
            }
            freshnessMerged = freshnessMerged
                    || ContentSuggestionType.FRESHNESS_UPDATE.name().equals(suggestion.getSuggestionType());
            publishSuggestionDecision(
                    suggestion, authorId, linkedDecision, resultVersion);
        }
        if (freshnessMerged) {
            FreshnessStatus previous = freshnessStatus(state);
            FreshnessStatus target = freshnessStatusAfterMergedSuggestions(postId, previous);
            if (previous == target) {
                return;
            }
            state.setFreshnessStatus(target.name());
            if (target == FreshnessStatus.UPDATED) {
                state.setLastConfirmedAt(LocalDateTime.now());
            }
            persistState(state);
            if (previous != target) {
                publishFreshnessState(post, authorId, previous, state);
            }
        }
    }

    private TrustedContentDTO toTrustedContent(PostDTO post, Long viewerUid, PostTrustStatePO state) {
        boolean communityQuestion = isCommunityQuestion(post);
        QuestionStatus currentQuestionStatus = communityQuestion ? questionStatus(state) : null;
        return TrustedContentDTO.builder()
                .postId(post.getId())
                .questionStatus(currentQuestionStatus)
                .allowedQuestionStatuses(communityQuestion
                        ? allowedQuestionStatuses(post.getId(), currentQuestionStatus)
                        : List.of())
                .freshnessStatus(freshnessStatus(state))
                .acceptedCommentId(state.getAcceptedCommentId())
                .duplicatePostId(state.getDuplicatePostId())
                .successorPostId(state.getSuccessorPostId())
                .lastConfirmedAt(state.getLastConfirmedAt())
                .suggestionsOpen(!Objects.equals(state.getSuggestionsOpen(), 0))
                .usefulFeedback(usefulFeedbackSummary(post.getId(), viewerUid))
                .publicSuggestionRecords(publicSuggestions(post.getId()))
                .build();
    }

    private UsefulFeedbackSummaryDTO usefulFeedbackSummary(Long postId, Long viewerUid) {
        Map<UsefulFeedbackReason, Long> counts = new EnumMap<>(UsefulFeedbackReason.class);
        USEFUL_REASONS.forEach(reason -> counts.put(reason, 0L));
        for (Map<String, Object> row : usefulFeedbackMapper.countByReason(postId)) {
            UsefulFeedbackReason reason = enumValue(
                    UsefulFeedbackReason.class, stringValue(row, "reason"));
            if (reason != null && USEFUL_REASONS.contains(reason)) {
                counts.put(reason, longValue(row, "feedbackCount"));
            }
        }
        UsefulFeedbackReason myReason = null;
        if (viewerUid != null && viewerUid > 0) {
            PostUsefulFeedbackPO mine = usefulFeedbackMapper.selectByUserAndPost(viewerUid, postId);
            myReason = mine == null ? null : enumValue(UsefulFeedbackReason.class, mine.getReason());
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return UsefulFeedbackSummaryDTO.builder()
                .total(total)
                .reasonCounts(counts)
                .myReason(myReason)
                .build();
    }

    private List<PublicContentSuggestionDTO> publicSuggestions(Long postId) {
        List<ContentSuggestionPO> rows = suggestionMapper.listPublicDecisions(
                postId, PUBLIC_SUGGESTION_LIMIT);
        Set<Long> attributedUids = rows.stream()
                .filter(row -> Objects.equals(row.getAllowPublicAttribution(), 1))
                .map(ContentSuggestionPO::getSubmitterUid)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, UserBriefDTO> users = attributedUids.isEmpty()
                ? Map.of()
                : userFacade.batchGetUserBriefs(attributedUids);
        return rows.stream()
                .map(row -> PublicContentSuggestionDTO.builder()
                        .suggestionId(row.getId())
                        .type(enumValue(ContentSuggestionType.class, row.getSuggestionType()))
                        .decision(enumValue(ContentSuggestionDecision.class, row.getDecision()))
                        .publicNote(row.getPublicNote())
                        .resultVersion(row.getResultVersion())
                        .decidedAt(row.getDecidedAt())
                        .submitterNickname(Objects.equals(row.getAllowPublicAttribution(), 1)
                                ? nickname(users.get(row.getSubmitterUid()))
                                : null)
                        .build())
                .toList();
    }

    private void markAwaitingAuthorConfirmation(PostDTO post, PostTrustStatePO state,
                                                Long actorUid, ContentSuggestionType type) {
        if (type != ContentSuggestionType.FRESHNESS_UPDATE) {
            return;
        }
        FreshnessStatus previous = freshnessStatus(state);
        if (previous == FreshnessStatus.AWAITING_AUTHOR_CONFIRMATION
                || previous == FreshnessStatus.SUPERSEDED) {
            return;
        }
        state.setFreshnessStatus(FreshnessStatus.AWAITING_AUTHOR_CONFIRMATION.name());
        persistState(state);
        publishFreshnessState(post, actorUid, previous, state);
    }

    private void handleFreshnessSuggestionDecision(PostDTO post, ContentSuggestionPO suggestion,
                                                   Long actorUid, ContentSuggestionDecision decision,
                                                   PostTrustStatePO state) {
        if (!ContentSuggestionType.FRESHNESS_UPDATE.name().equals(suggestion.getSuggestionType())) {
            return;
        }
        FreshnessStatus previous = freshnessStatus(state);
        if (previous == FreshnessStatus.SUPERSEDED) {
            return;
        }
        long pending = suggestionMapper.countPendingByPostAndType(
                post.getId(), ContentSuggestionType.FRESHNESS_UPDATE.name());
        FreshnessStatus target;
        if (pending > 0) {
            target = FreshnessStatus.AWAITING_AUTHOR_CONFIRMATION;
        } else if (decision == ContentSuggestionDecision.REJECTED) {
            target = FreshnessStatus.CURRENT;
        } else {
            target = FreshnessStatus.POSSIBLY_STALE;
        }
        if (previous == target) {
            return;
        }
        state.setFreshnessStatus(target.name());
        if (target == FreshnessStatus.CURRENT) {
            state.setLastConfirmedAt(LocalDateTime.now());
        }
        persistState(state);
        publishFreshnessState(post, actorUid, previous, state);
    }

    private FreshnessStatus freshnessStatusAfterMergedSuggestions(Long postId, FreshnessStatus previous) {
        if (previous == FreshnessStatus.SUPERSEDED) {
            return FreshnessStatus.SUPERSEDED;
        }
        long pending = suggestionMapper.countPendingByPostAndType(
                postId, ContentSuggestionType.FRESHNESS_UPDATE.name());
        return pending > 0
                ? FreshnessStatus.AWAITING_AUTHOR_CONFIRMATION
                : FreshnessStatus.UPDATED;
    }

    private PostTrustStatePO lockState(PostDTO post) {
        PostTrustStatePO state = lockState(post.getId(), QuestionStatus.OPEN.name());
        if (isCommunityQuestion(post) && state.getQuestionStatus() == null) {
            state.setQuestionStatus(QuestionStatus.OPEN.name());
            persistState(state);
        }
        return state;
    }

    private PostTrustStatePO lockState(Long postId, String initialQuestionStatus) {
        trustStateMapper.insertIfAbsent(
                postId,
                initialQuestionStatus,
                FreshnessStatus.CURRENT.name(),
                1);
        PostTrustStatePO state = trustStateMapper.selectForUpdate(postId);
        if (state == null) {
            throw new BizException(ErrorCode.DATABASE_ERROR);
        }
        return state;
    }

    private PostTrustStatePO currentState(PostDTO post) {
        PostTrustStatePO state = trustStateMapper.selectByPostId(post.getId());
        return state == null ? defaultState(post) : state;
    }

    private static PostTrustStatePO defaultState(PostDTO post) {
        PostTrustStatePO state = new PostTrustStatePO();
        state.setPostId(post.getId());
        state.setQuestionStatus(isCommunityQuestion(post) ? QuestionStatus.OPEN.name() : null);
        state.setFreshnessStatus(FreshnessStatus.CURRENT.name());
        state.setSuggestionsOpen(1);
        return state;
    }

    private void persistState(PostTrustStatePO state) {
        if (trustStateMapper.updateState(state) <= 0) {
            throw new BizException(ErrorCode.DATABASE_ERROR);
        }
    }

    private List<QuestionStatus> allowedQuestionStatuses(Long postId, QuestionStatus current) {
        long availableAnswers = commentMapper.countAvailableRootComments(postId);
        return QUESTION_STATUSES.stream()
                .filter(target -> isQuestionStatusAllowed(current, target, availableAnswers))
                .toList();
    }

    private static boolean isQuestionStatusAllowed(
            QuestionStatus current, QuestionStatus target, long availableAnswers) {
        return current != null
                && target != null
                && target != QuestionStatus.ACCEPTED
                && current.canTransitionTo(target)
                && (target != QuestionStatus.ANSWERED || availableAnswers > 0)
                && (target != QuestionStatus.OPEN || availableAnswers <= 0);
    }

    private QuestionStatus statusFromAvailableAnswers(Long postId) {
        return commentMapper.countAvailableRootComments(postId) > 0
                ? QuestionStatus.ANSWERED
                : QuestionStatus.OPEN;
    }

    private boolean isFolded(Long commentId) {
        CommentQualitySignalPO signal = commentQualitySignalMapper.selectByCommentAndType(
                commentId, FOLDED_SIGNAL);
        return signal != null
                && Objects.equals(signal.getSignalStatus(), 1)
                && !Objects.equals(signal.getIsDeleted(), 1);
    }

    private void requirePublicQuestionTarget(Long sourcePostId, Long targetPostId) {
        PostDTO target = requirePublicTarget(sourcePostId, targetPostId);
        if (!Objects.equals(target.getPostType(), Post.TYPE_COMMUNITY_QUESTION)) {
            throw parameterError("duplicate target must be a community question");
        }
    }

    private void requirePublicSuccessor(Long sourcePostId, Long successorPostId) {
        requirePublicTarget(sourcePostId, successorPostId);
    }

    private PostDTO requirePublicTarget(Long sourcePostId, Long targetPostId) {
        if (targetPostId == null || targetPostId <= 0 || Objects.equals(sourcePostId, targetPostId)) {
            throw parameterError("target post must be different");
        }
        PostDTO target = postFacade.getPost(targetPostId, null);
        if (target == null
                || !Objects.equals(target.getVisibility(), Post.VIS_PUBLIC)
                || !Objects.equals(target.getPostStatus(), Post.STATUS_PUBLISHED)) {
            throw parameterError("target post must be public and published");
        }
        return target;
    }

    private PostDTO requireVisibleQuestion(Long postId, Long viewerUid) {
        PostDTO post = requireVisiblePost(postId, viewerUid);
        if (!Objects.equals(post.getPostType(), Post.TYPE_COMMUNITY_QUESTION)) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                    "operation is only available for community questions");
        }
        return post;
    }

    private PostDTO requireVisiblePost(Long postId, Long viewerUid) {
        requirePositive(postId);
        PostDTO post = postFacade.getPost(postId, viewerUid);
        if (post == null) {
            throw new BizException(ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    private void requirePostAuthor(PostDTO post, Long actorUid) {
        if (post.getAuthorId() == null || !post.getAuthorId().equals(actorUid)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private void requireNotAuthor(PostDTO post, Long userId) {
        if (post.getAuthorId() != null && post.getAuthorId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                    "authors cannot give themselves trusted-content feedback");
        }
    }

    private static boolean isCommunityQuestion(PostDTO post) {
        return post != null && Objects.equals(post.getPostType(), Post.TYPE_COMMUNITY_QUESTION);
    }

    private Map<Long, UserBriefDTO> loadUsers(Collection<ContentSuggestionPO> rows) {
        Set<Long> uids = rows == null ? Set.of() : rows.stream()
                .map(ContentSuggestionPO::getSubmitterUid)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return uids.isEmpty() ? Map.of() : userFacade.batchGetUserBriefs(uids);
    }

    private static ContentSuggestionDTO toSuggestionDTO(
            ContentSuggestionPO suggestion, Map<Long, UserBriefDTO> users) {
        ContentSuggestionResolution resolution = effectiveResolution(suggestion);
        ContentSuggestionDeliveryStatus deliveryStatus = effectiveDeliveryStatus(suggestion);
        ContentSuggestionDecision decision = enumValue(
                ContentSuggestionDecision.class, suggestion.getDecision());
        if (decision == null) {
            decision = resolution.toCompatibleDecision();
        }
        return ContentSuggestionDTO.builder()
                .id(suggestion.getId())
                .postId(suggestion.getPostId())
                .postAuthorUid(suggestion.getPostAuthorId())
                .submitterUid(suggestion.getSubmitterUid())
                .submitterNickname(nickname(users.get(suggestion.getSubmitterUid())))
                .type(enumValue(ContentSuggestionType.class, suggestion.getSuggestionType()))
                .detail(suggestion.getDetail())
                .sourceUrl(suggestion.getSourceUrl())
                .baseVersion(suggestion.getBaseVersion())
                .targetScope(enumValue(ContentSuggestionTargetScope.class, suggestion.getTargetScope()))
                .targetLocator(suggestion.getTargetLocator())
                .expectedChange(suggestion.getExpectedChange())
                .allowPublicAttribution(Objects.equals(suggestion.getAllowPublicAttribution(), 1))
                .status(resolution == ContentSuggestionResolution.PENDING
                        ? ContentSuggestionStatus.PENDING
                        : ContentSuggestionStatus.DECIDED)
                .decision(decision)
                .resolution(resolution)
                .deliveryStatus(deliveryStatus)
                .authorReply(suggestion.getAuthorReply())
                .publicNote(suggestion.getPublicNote())
                .resultVersion(suggestion.getResultVersion())
                .decidedAt(suggestion.getDecidedAt())
                .createTime(suggestion.getCreateTime())
                .updateTime(suggestion.getUpdateTime())
                .build();
    }

    private void publishUsefulFeedback(PostDTO post, Long userId,
                                       UsefulFeedbackReason reason,
                                       UsefulFeedbackReason previousReason,
                                       boolean active) {
        events.publish(PostUsefulFeedbackChangedEvent.builder()
                .postId(post.getId())
                .postAuthorUid(post.getAuthorId())
                .userId(userId)
                .reason(reason == null ? null : reason.name())
                .previousReason(previousReason == null ? null : previousReason.name())
                .active(active)
                .timestamp(Instant.now().toEpochMilli())
                .build());
    }

    private void publishAnswerInvalidated(Long postId, Long commentId, Long actorUid, String reason) {
        if (postId == null || commentId == null) {
            return;
        }
        events.publish(AnswerAcceptanceInvalidatedEvent.builder()
                .postId(postId)
                .commentId(commentId)
                .actorUid(actorUid)
                .reason(reason)
                .timestamp(Instant.now().toEpochMilli())
                .build());
    }

    private void publishSuggestionDecision(ContentSuggestionPO suggestion, Long actorUid,
                                           ContentSuggestionDecision decision, Integer resultVersion) {
        events.publish(ContentSuggestionDecidedEvent.builder()
                .suggestionId(suggestion.getId())
                .postId(suggestion.getPostId())
                .postAuthorUid(suggestion.getPostAuthorId())
                .submitterUid(suggestion.getSubmitterUid())
                .actorUid(actorUid)
                .decision(decision.name())
                .resultVersion(resultVersion)
                .timestamp(Instant.now().toEpochMilli())
                .build());
    }

    private void publishQuestionState(PostDTO post, Long actorUid,
                                      QuestionStatus previous, PostTrustStatePO state) {
        QuestionStatus current = questionStatus(state);
        events.publish(QuestionStateChangedEvent.builder()
                .postId(post.getId())
                .postAuthorUid(post.getAuthorId())
                .actorUid(actorUid)
                .previousStatus(previous == null ? null : previous.name())
                .questionStatus(current.name())
                .acceptedCommentId(state.getAcceptedCommentId())
                .duplicatePostId(state.getDuplicatePostId())
                .timestamp(Instant.now().toEpochMilli())
                .build());
    }

    private void publishFreshnessState(PostDTO post, Long actorUid,
                                       FreshnessStatus previous, PostTrustStatePO state) {
        FreshnessStatus current = freshnessStatus(state);
        events.publish(PostFreshnessChangedEvent.builder()
                .postId(post.getId())
                .postAuthorUid(post.getAuthorId())
                .actorUid(actorUid)
                .previousStatus(previous == null ? null : previous.name())
                .freshnessStatus(current.name())
                .successorPostId(state.getSuccessorPostId())
                .timestamp(Instant.now().toEpochMilli())
                .build());
    }

    private static QuestionStatus questionStatus(PostTrustStatePO state) {
        QuestionStatus status = enumValue(QuestionStatus.class, state.getQuestionStatus());
        return status == null ? QuestionStatus.OPEN : status;
    }

    private static boolean canAcceptAnswerFrom(QuestionStatus status) {
        return status == QuestionStatus.OPEN
                || status == QuestionStatus.ANSWERED
                || status == QuestionStatus.ACCEPTED;
    }

    private static FreshnessStatus freshnessStatus(PostTrustStatePO state) {
        FreshnessStatus status = enumValue(FreshnessStatus.class, state.getFreshnessStatus());
        return status == null ? FreshnessStatus.CURRENT : status;
    }

    private static int clampSuggestionLimit(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 20 : limit, MAX_SUGGESTION_LIST_LIMIT));
    }

    private static ContentSuggestionDecision requestedDecision(ContentSuggestionDecisionCmd cmd) {
        if (cmd == null) {
            return null;
        }
        if (cmd.getDecision() != null) {
            return cmd.getDecision();
        }
        return cmd.getResolution() == null ? null : cmd.getResolution().toCompatibleDecision();
    }

    private static ContentSuggestionResolution requestedResolution(
            ContentSuggestionDecisionCmd cmd, ContentSuggestionDecision decision) {
        ContentSuggestionResolution fromDecision = ContentSuggestionResolution.fromDecision(decision);
        if (cmd == null || cmd.getResolution() == null) {
            return fromDecision;
        }
        if (decision != null && cmd.getResolution() != fromDecision) {
            throw parameterError("content suggestion decision and resolution conflict");
        }
        return cmd.getResolution();
    }

    private static ContentSuggestionResolution effectiveResolution(ContentSuggestionPO suggestion) {
        ContentSuggestionResolution persisted = enumValue(
                ContentSuggestionResolution.class, suggestion.getResolution());
        if (persisted != null && persisted != ContentSuggestionResolution.PENDING) {
            return persisted;
        }
        ContentSuggestionDecision legacyDecision = enumValue(
                ContentSuggestionDecision.class, suggestion.getDecision());
        return ContentSuggestionResolution.fromDecision(legacyDecision);
    }

    private static ContentSuggestionDeliveryStatus effectiveDeliveryStatus(ContentSuggestionPO suggestion) {
        ContentSuggestionDeliveryStatus persisted = enumValue(
                ContentSuggestionDeliveryStatus.class, suggestion.getDeliveryStatus());
        if (persisted == ContentSuggestionDeliveryStatus.LINKED
                || suggestion.getResultVersion() != null) {
            return ContentSuggestionDeliveryStatus.LINKED;
        }
        return ContentSuggestionDeliveryStatus.UNLINKED;
    }

    private static ContentSuggestionDecision compatibleDecision(
            ContentSuggestionPO suggestion, ContentSuggestionResolution resolution) {
        ContentSuggestionDecision persisted = enumValue(
                ContentSuggestionDecision.class, suggestion.getDecision());
        return persisted == null ? resolution.toCompatibleDecision() : persisted;
    }

    private static String normalizeSourceUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String raw = normalizeRequired(value, MAX_SOURCE_URL_LENGTH, "source URL");
        try {
            URI uri = new URI(raw).normalize();
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!uri.isAbsolute()
                    || (!"http".equals(scheme) && !"https".equals(scheme))
                    || uri.getHost() == null
                    || uri.getHost().isBlank()
                    || uri.getUserInfo() != null) {
                throw parameterError("source URL must be an absolute http/https URL");
            }
            return uri.toASCIIString();
        } catch (URISyntaxException e) {
            throw parameterError("source URL is invalid");
        }
    }

    private static String normalizeForHash(String value) {
        return value.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeRequired(String value, int maxLength, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw parameterError(field + " is invalid");
        }
        return normalized;
    }

    private static String normalizeOptional(String value, int maxLength, String field) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw parameterError(field + " is too long");
        }
        return normalized;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String nickname(UserBriefDTO user) {
        return user == null ? null : user.getNickname();
    }

    private static String stringValue(Map<String, Object> row, String key) {
        Object value = mapValue(row, key);
        return value == null ? null : String.valueOf(value);
    }

    private static long longValue(Map<String, Object> row, String key) {
        Object value = mapValue(row, key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static Object mapValue(Map<String, Object> row, String key) {
        if (row == null) {
            return null;
        }
        Object direct = row.get(key);
        if (direct != null) {
            return direct;
        }
        return row.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().equalsIgnoreCase(key))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void requirePositive(Long value) {
        if (value == null || value <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private static BizException parameterError(String message) {
        return new BizException(ErrorCode.PARAM_ERROR.getCode(), message);
    }
}
