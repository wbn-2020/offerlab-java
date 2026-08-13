package com.offerlab.community.post.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.mq.producer.EventPublisher;
import com.offerlab.community.infra.moderation.ContentModerationSourceAuthorizationHandler;
import com.offerlab.community.infra.moderation.ContentModerationService;
import com.offerlab.community.infra.moderation.ModerationKeywordHit;
import com.offerlab.community.infra.redis.cache.PostCounterRedis;
import com.offerlab.community.infra.redis.cache.CacheKeyBuilder;
import com.offerlab.community.infra.redis.cache.MultiLevelCache;
import com.offerlab.community.infra.review.ReviewQueueItemCommand;
import com.offerlab.community.infra.review.ReviewQueueReopenRequestedEvent;
import com.offerlab.community.infra.tx.AfterCommitExecutor;
import com.offerlab.community.post.api.dto.PostCreateCmd;
import com.offerlab.community.post.api.dto.PostContentLimits;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.api.dto.PostUpdateCmd;
import com.offerlab.community.post.api.event.PostDeletedEvent;
import com.offerlab.community.post.api.event.PostPublishedEvent;
import com.offerlab.community.post.api.event.PostUpdatedEvent;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.domain.model.PostDomain;
import com.offerlab.community.post.domain.repository.PostRepository;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostCounterMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostTagRefMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.TagMapper;
import com.offerlab.community.post.infrastructure.persistence.po.TagPO;
import com.offerlab.community.post.infrastructure.persistence.projection.PostTagView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostApplicationService implements ContentModerationSourceAuthorizationHandler {

    static final String PENDING_REVIEW_SOURCE_TYPE = "POST_PENDING_REVIEW";

    private static final int MAX_PUBLIC_UPDATE_SUMMARY_LEN = 500;
    private static final int MAX_IMPACT_SCOPE_LEN = 255;
    private static final int MAX_RESPONDED_SUGGESTION_IDS = 100;

    private final PostRepository postRepo;
    private final PostCounterMapper counterMapper;
    private final PostTagRefMapper postTagRefMapper;
    private final TagMapper tagMapper;
    private final PostCounterRedis postCounterRedis;
    private final SnowflakeIdGenerator idGen;
    private final EventPublisher events;
    private final PostVersionHistoryService versionHistoryService;
    private final PostPublishQualityValidator qualityValidator;
    private final AfterCommitExecutor afterCommit;
    private final CommunityTopicNotificationTargetService communityTopicNotificationTargetService;
    private final MigrationCheckService migrationCheckService;
    private final DomainConfigService domainConfigService;
    private final ContentModerationService contentModerationService;
    private final MultiLevelCache<PostDTO> postDetailCache;
    private final DomainModeratorService domainModeratorService;
    private final ApplicationEventPublisher springEvents;

    @Transactional
    public Long publish(PostCreateCmd cmd) {
        Integer domain = requirePublishDomain(cmd.getDomain());
        domainConfigService.requireDomainEnabled(domain);
        PostPublishQualityValidator.ValidatedPostInput input = qualityValidator.validate(
                cmd.getPostType(), cmd.getTitle(), cmd.getContent(), cmd.getExtJson(), cmd.getTagIds(), cmd.getTagNames());
        long id = cmd.getPostId() == null || cmd.getPostId() <= 0 ? idGen.nextId() : cmd.getPostId();
        List<Long> resolvedTagIds = resolveTagIds(input.tagIds(), input.tagNames());
        requireResolvedTagCount(input.postType(), resolvedTagIds);
        String enrichedExtJson = mergeAnonymousToExtJson(
                mergeDomainToExtJson(input.extJson(), domain), domain, cmd.getAnonymous());
        boolean policyReviewRequired = domainConfigService.reviewRequiredForPublish(domain);
        boolean reviewRequired = Boolean.TRUE.equals(cmd.getReviewRequired()) || policyReviewRequired;
        boolean keywordReviewRequired = Boolean.TRUE.equals(cmd.getKeywordReviewRequired());
        Post post = Post.builder()
                .id(id)
                .authorId(cmd.getAuthorId())
                .postType(input.postType())
                .title(input.title())
                .content(input.content())
                .coverUrl(cmd.getCoverUrl())
                .visibility(cmd.getVisibility() == null ? Post.VIS_PUBLIC : cmd.getVisibility())
                .postStatus(reviewRequired ? Post.STATUS_REVIEWING : Post.STATUS_PUBLISHED)
                .extJson(enrichedExtJson)
                .tagIds(resolvedTagIds)
                .domain(domain)
                .build();
        postRepo.save(post);
        counterMapper.initIfAbsent(id);
        syncTags(id, post.getTagIds());

        if (reviewRequired) {
            if (!keywordReviewRequired) {
                enqueuePendingPostReview(post, pendingReviewReason(post, policyReviewRequired));
            }
        } else {
            publishPostPublishedEvent(post, resolvedTagIds);
        }
        return id;
    }

    @Transactional
    public void resolveModerationHit(Long hitId, Long reviewerUid, boolean approved, String note) {
        ModerationKeywordHit hit = contentModerationService.findKeywordHit(hitId);
        if (hit == null || !ContentModerationService.SCOPE_POST.equalsIgnoreCase(hit.getScope())
                || !ContentModerationService.SOURCE_POST.equalsIgnoreCase(hit.getSourceType())
                || hit.getSourceId() == null || hit.getSourceId() <= 0) {
            return;
        }
        Post post = postRepo.findById(hit.getSourceId())
                .orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
        domainModeratorService.requireModerateDomain(reviewerUid, post.getDomain());
        requireIndependentReviewer(post, reviewerUid);
        if (!Objects.equals(post.getPostStatus(), Post.STATUS_REVIEWING)) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        Integer nextStatus = approved ? Post.STATUS_PUBLISHED : Post.STATUS_TAKEN_DOWN;
        boolean updated = postRepo.updateStatusIfCurrent(
                post.getId(), Post.STATUS_REVIEWING, nextStatus, post.getVersion());
        if (!updated) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        post.setPostStatus(nextStatus);
        versionHistoryService.resolvePendingPublicRevision(post.getId(), post.getVersion(), approved);
        if (approved) {
            publishPostPublishedEvent(post, currentTagIds(post.getId()));
        }
        evictPostDetailAfterCommit(post.getId());
        contentModerationService.reviewKeywordHit(hitId, approved ? "APPROVED" : "REJECTED", reviewerUid, note);
    }

    @Transactional
    public boolean update(PostUpdateCmd cmd) {
        Post post = postRepo.findById(cmd.getPostId())
                .orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
        if (!post.getAuthorId().equals(cmd.getOperatorUid())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        requireEditableStatus(post);
        String publicUpdateSummary = normalizeOptionalUpdateText(
                cmd.getPublicUpdateSummary(), MAX_PUBLIC_UPDATE_SUMMARY_LEN,
                "publicUpdateSummary", "公开更新摘要不能超过 500 个字符");
        String impactScope = normalizeOptionalUpdateText(
                cmd.getImpactScope(), MAX_IMPACT_SCOPE_LEN,
                "impactScope", "影响范围不能超过 255 个字符");
        List<Long> respondedSuggestionIds = normalizeRespondedSuggestionIds(cmd.getRespondedSuggestionIds());
        if (!respondedSuggestionIds.isEmpty() && publicUpdateSummary == null) {
            throw PostPublishQualityValidator.fieldError(
                    "publicUpdateSummary", "处理内容建议时必须提供公开更新摘要");
        }
        if (impactScope != null && publicUpdateSummary == null) {
            throw PostPublishQualityValidator.fieldError(
                    "publicUpdateSummary", "填写影响范围时必须提供公开更新摘要");
        }
        Integer nextDomain = cmd.getDomain() == null ? requireDomain(post.getDomain()) : requireDomain(cmd.getDomain());
        domainConfigService.requireDomainEnabled(nextDomain);
        boolean tagsProvided = cmd.getTagIds() != null || cmd.getTagNames() != null;
        List<Long> existingTagIds = currentTagIds(post.getId());
        List<Long> validationTagIds = tagsProvided ? cmd.getTagIds() : existingTagIds;
        List<String> validationTagNames = tagsProvided ? cmd.getTagNames() : List.of();
        PostPublishQualityValidator.ValidatedPostInput input = qualityValidator.validate(
                post.getPostType(),
                cmd.getTitle() == null ? post.getTitle() : cmd.getTitle(),
                cmd.getContent() == null ? post.getContent() : cmd.getContent(),
                cmd.getExtJson() == null ? post.getExtJson() : cmd.getExtJson(),
                validationTagIds,
                validationTagNames);
        List<Long> resolvedTagIds = tagsProvided ? resolveTagIds(input.tagIds(), input.tagNames()) : validationTagIds;
        if (tagsProvided) {
            requireResolvedTagCount(input.postType(), resolvedTagIds);
        }
        String enrichedExtJson = mergeAnonymousToExtJson(
                mergeDomainToExtJson(input.extJson(), nextDomain), nextDomain, cmd.getAnonymous());
        String nextCoverUrl = cmd.getCoverUrl() == null ? post.getCoverUrl() : cmd.getCoverUrl();
        Integer nextVisibility = cmd.getVisibility() == null ? post.getVisibility() : cmd.getVisibility();
        boolean policyReviewRequired = domainConfigService.reviewRequiredForPublish(nextDomain);
        boolean reviewRequired = Boolean.TRUE.equals(cmd.getReviewRequired()) || policyReviewRequired;
        boolean keywordReviewRequired = Boolean.TRUE.equals(cmd.getKeywordReviewRequired());
        boolean forceVersionSnapshot = !respondedSuggestionIds.isEmpty()
                || publicUpdateSummary != null
                || impactScope != null;
        boolean qualityRevisionCandidate = versionHistoryService.isEligiblePublicTextRevision(
                post, input.title(), input.content(), nextVisibility);
        PostVersionHistoryService.ContentRevisionCandidate contentRevisionCandidate =
                versionHistoryService.snapshotBeforeUpdate(
                post, cmd.getOperatorUid(), tagsByIds(existingTagIds), post.getVersion(),
                input.title(), input.content(), nextCoverUrl, nextVisibility, enrichedExtJson, resolvedTagIds, tagsProvided,
                publicUpdateSummary, impactScope, forceVersionSnapshot, qualityRevisionCandidate);

        post.setVisibility(nextVisibility);
        post.setExtJson(enrichedExtJson);
        post.setDomain(nextDomain);
        post.setTitle(input.title());
        post.setContent(input.content());
        post.setCoverUrl(nextCoverUrl);
        if (reviewRequired) {
            post.setPostStatus(Post.STATUS_REVIEWING);
        }
        if (!postRepo.update(post)) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        if (tagsProvided) {
            syncTags(post.getId(), resolvedTagIds);
        }
        if (contentRevisionCandidate != null && !reviewRequired) {
            versionHistoryService.activateDirectPublicRevision(contentRevisionCandidate);
        }
        if (reviewRequired && !keywordReviewRequired) {
            enqueuePendingPostReview(post, pendingReviewReason(post, policyReviewRequired));
        }
        boolean includeEventContent = post.getPostStatus() != null
                && post.getPostStatus() == Post.STATUS_PUBLISHED
                && (post.getVisibility() == null || post.getVisibility() == Post.VIS_PUBLIC);
        events.publish(PostUpdatedEvent.builder()
                .postId(post.getId())
                .authorId(post.getAuthorId())
                .title(includeEventContent ? post.getTitle() : null)
                .content(includeEventContent ? post.getContent() : null)
                .visibility(post.getVisibility())
                .postStatus(post.getPostStatus())
                .resultVersion(post.getVersion())
                .respondedSuggestionIds(respondedSuggestionIds)
                .timestamp(Instant.now().toEpochMilli())
                .build());
        return Objects.equals(post.getPostStatus(), Post.STATUS_REVIEWING);
    }

    @Override
    public boolean supports(String scope, String sourceType) {
        return ContentModerationService.SCOPE_POST.equalsIgnoreCase(scope)
                && ContentModerationService.SOURCE_POST.equalsIgnoreCase(sourceType);
    }

    @Override
    public void requireAuthorized(Long uid, Long sourceId) {
        if (sourceId == null || sourceId <= 0) {
            return;
        }
        Post post = postRepo.findById(sourceId)
                .orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
        if (!Objects.equals(post.getAuthorId(), uid)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    @Transactional
    public void resolvePendingPostReview(Long postId, Long reviewerUid, boolean approved, String note) {
        resolvePendingPostReview(postId, reviewerUid, approved, note, null);
    }

    @Transactional
    public void resolvePendingPostReview(Long postId, Long reviewerUid, boolean approved, String note,
                                         Integer expectedVersion) {
        Post post = postRepo.findById(postId)
                .orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
        domainModeratorService.requireModerateDomain(reviewerUid, post.getDomain());
        requireIndependentReviewer(post, reviewerUid);
        if (expectedVersion != null && !Objects.equals(post.getVersion(), expectedVersion)) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "帖子内容已更新，请重新预览后审核");
        }
        if (!Objects.equals(post.getPostStatus(), Post.STATUS_REVIEWING)) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        Integer nextStatus = approved ? Post.STATUS_PUBLISHED : Post.STATUS_TAKEN_DOWN;
        boolean updated = postRepo.updateStatusIfCurrent(
                post.getId(), Post.STATUS_REVIEWING, nextStatus, post.getVersion());
        if (!updated) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        post.setPostStatus(nextStatus);
        versionHistoryService.resolvePendingPublicRevision(post.getId(), post.getVersion(), approved);
        if (approved) {
            publishPostPublishedEvent(post, currentTagIds(post.getId()));
        }
        evictPostDetailAfterCommit(post.getId());
    }

    @Transactional
    public void delete(Long postId, Long operatorUid) {
        Post post = postRepo.findById(postId)
                .orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
        if (!post.getAuthorId().equals(operatorUid)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        postRepo.softDelete(postId);
        events.publish(PostDeletedEvent.builder()
                .postId(postId)
                .authorId(post.getAuthorId())
                .timestamp(Instant.now().toEpochMilli())
                .build());
        afterCommit.execute(() -> postCounterRedis.evict(postId), "post counter eviction:" + postId);
    }

    public Post getOrThrow(Long postId) {
        return postRepo.findById(postId).orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
    }

    public void incrView(Long postId) {
        counterMapper.incrView(postId, 1);
        afterCommit.execute(() -> postCounterRedis.evict(postId), "post view counter invalidation:" + postId);
    }

    private String mergeDomainToExtJson(String extJson, Integer domain) {
        if (domain == null) return extJson;
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root;
            if (extJson != null && !extJson.isBlank()) {
                root = mapper.readTree(extJson);
                if (root instanceof ObjectNode obj) {
                    obj.put("domain", domain);
                    return mapper.writeValueAsString(obj);
                }
            }
            // No existing extJson, create new
            ObjectNode obj = mapper.createObjectNode();
            obj.put("domain", domain);
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            // If can't parse, return original
            return extJson;
        }
    }

    private String mergeAnonymousToExtJson(String extJson, Integer domain, Boolean anonymous) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode object = readObjectExtJson(mapper, extJson);
            Integer effectiveDomain = domain != null ? domain : readDomain(object);
            boolean hasAnonymous = object.has("anonymous");
            if (anonymous == null && !hasAnonymous) {
                return extJson;
            }
            boolean enabled = Objects.equals(effectiveDomain, Post.DOMAIN_CAREER) && Boolean.TRUE.equals(
                    anonymous == null ? object.path("anonymous").asBoolean(false) : anonymous);
            object.put("anonymous", enabled);
            return mapper.writeValueAsString(object);
        } catch (Exception e) {
            return extJson;
        }
    }

    private ObjectNode readObjectExtJson(ObjectMapper mapper, String extJson) throws Exception {
        if (extJson != null && !extJson.isBlank()) {
            JsonNode root = mapper.readTree(extJson);
            if (root instanceof ObjectNode obj) {
                return obj;
            }
        }
        return mapper.createObjectNode();
    }

    private Integer readDomain(ObjectNode object) {
        if (object != null && object.has("domain") && object.get("domain").canConvertToInt()) {
            return object.get("domain").asInt();
        }
        return null;
    }

    private Integer requirePublishDomain(Integer domain) {
        if (domain == null) {
            throw PostPublishQualityValidator.fieldError("domain", "请选择频道");
        }
        return requireDomain(domain);
    }

    private Integer requireDomain(Integer domain) {
        if (PostDomain.isValid(domain)) {
            return domain;
        }
        throw PostPublishQualityValidator.fieldError("domain", "领域不存在或已下线");
    }

    private String normalizeOptionalUpdateText(String value, int maxLength, String field, String message) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw PostPublishQualityValidator.fieldError(field, message);
        }
        return normalized;
    }

    private List<Long> normalizeRespondedSuggestionIds(List<Long> suggestionIds) {
        if (suggestionIds == null || suggestionIds.isEmpty()) {
            return List.of();
        }
        if (suggestionIds.size() > MAX_RESPONDED_SUGGESTION_IDS) {
            throw PostPublishQualityValidator.fieldError(
                    "respondedSuggestionIds", "一次最多关联 100 条建议");
        }
        for (Long suggestionId : suggestionIds) {
            if (suggestionId == null || suggestionId <= 0) {
                throw PostPublishQualityValidator.fieldError(
                        "respondedSuggestionIds", "建议 ID 必须为正整数");
            }
        }
        return suggestionIds.stream().distinct().toList();
    }

    private List<Long> resolveTagIds(List<Long> tagIds, List<String> tagNames) {
        Set<Long> ids = new LinkedHashSet<>();
        if (tagIds != null) {
            List<Long> requestedIds = tagIds.stream()
                    .filter(id -> id != null && id > 0)
                    .toList();
            if (!requestedIds.isEmpty()) {
                Set<Long> existingIds = selectTagsByIds(requestedIds).stream()
                        .filter(tag -> !java.util.Objects.equals(tag.getTagStatus(), 0))
                        .map(TagPO::getId)
                        .collect(Collectors.toCollection(HashSet::new));
                for (Long id : requestedIds) {
                    if (!existingIds.contains(id)) {
                        throw PostPublishQualityValidator.fieldError("tags", "标签不存在、已删除或已被禁用");
                    }
                    ids.add(id);
                }
            }
        }
        if (tagNames != null && !tagNames.isEmpty()) {
            List<String> names = tagNames.stream()
                    .filter(name -> name != null && !name.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
            if (!names.isEmpty()) {
                List<TagPO> existingTags = selectTagsByNames(names);
                Set<String> existingNames = existingTags.stream()
                        .map(TagPO::getTagName)
                        .map(name -> name.toLowerCase(Locale.ROOT))
                        .collect(Collectors.toSet());
                existingTags.stream().map(TagPO::getId).forEach(ids::add);
                List<String> missingNames = names.stream()
                        .filter(name -> !existingNames.contains(name.toLowerCase(Locale.ROOT)))
                        .toList();
                if (ids.size() + missingNames.size() > PostContentLimits.MAX_TAG_COUNT) {
                    throw PostPublishQualityValidator.fieldError(
                            "tags", "最多只能选择 " + PostContentLimits.MAX_TAG_COUNT + " 个标签");
                }
                for (String name : missingNames) {
                        insertIgnoreName(idGen.nextId(), name, 4);
                }
                selectTagsByNames(missingNames).stream()
                        .map(TagPO::getId)
                        .forEach(ids::add);
            }
        }
        if (tagIds == null && tagNames == null) {
            return null;
        }
        if (ids.size() > PostContentLimits.MAX_TAG_COUNT) {
            throw PostPublishQualityValidator.fieldError(
                    "tags", "最多只能选择 " + PostContentLimits.MAX_TAG_COUNT + " 个标签");
        }
        return List.copyOf(ids);
    }

    private void requireResolvedTagCount(Integer postType, List<Long> tagIds) {
        int min = Post.isInterviewType(postType) ? 2 : 1;
        if (tagIds == null || tagIds.size() < min) {
            throw PostPublishQualityValidator.fieldError("tags", Post.isInterviewType(postType)
                    ? "历史经验至少需要 2 个有效技术标签"
                    : "至少需要 1 个有效标签");
        }
        if (tagIds.size() > PostContentLimits.MAX_TAG_COUNT) {
            throw PostPublishQualityValidator.fieldError(
                    "tags", "最多只能选择 " + PostContentLimits.MAX_TAG_COUNT + " 个标签");
        }
    }

    private List<Long> currentTagIds(Long postId) {
        return selectTagsByPostIds(List.of(postId)).stream()
                .map(PostTagView::getId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    private void enqueuePendingPostReview(Post post, String reason) {
        String riskLevel = domainConfigService.riskLevelForDomain(post.getDomain());
        if (Objects.equals(post.getDomain(), Post.DOMAIN_INVESTMENT)) {
            riskLevel = "high";
        }
        String domainName = domainDisplayName(post.getDomain());
        String title = post.getTitle() == null || post.getTitle().isBlank()
                ? "帖子待审核 " + post.getId()
                : "帖子待审核：" + post.getTitle();
        String summary = "频道：" + domainName
                + " / 触发原因：" + reason
                + " / 内容：" + reviewContentSummary(post.getContent());
        springEvents.publishEvent(new ReviewQueueReopenRequestedEvent(new ReviewQueueItemCommand(
                PENDING_REVIEW_SOURCE_TYPE,
                post.getId(),
                title,
                summary,
                riskLevel,
                post.getAuthorId(),
                reviewPriority(riskLevel),
                "{\"postId\":" + post.getId()
                        + ",\"domain\":" + post.getDomain()
                        + ",\"version\":" + (post.getVersion() == null ? 0 : post.getVersion()) + "}",
                "post pending review"
        )));
    }

    private String pendingReviewReason(Post post, boolean policyReviewRequired) {
        if (policyReviewRequired) {
            return domainDisplayName(post.getDomain()) + "频道发布策略要求人工审核";
        }
        return "调用方要求人工审核";
    }

    private String domainDisplayName(Integer domain) {
        return PostDomain.isValid(domain) ? PostDomain.fromCode(domain).getDisplayName() : "未标注频道";
    }

    private int reviewPriority(String riskLevel) {
        return switch (riskLevel == null ? "" : riskLevel.toLowerCase()) {
            case "high" -> 80;
            case "medium" -> 60;
            default -> 50;
        };
    }

    private String reviewContentSummary(String content) {
        String normalized = content == null ? "" : content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 600 ? normalized : normalized.substring(0, 600);
    }

    private void requireIndependentReviewer(Post post, Long reviewerUid) {
        if (post != null && Objects.equals(post.getAuthorId(), reviewerUid)) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(), "帖子作者不能审核自己的内容");
        }
    }

    private void publishPostPublishedEvent(Post post, List<Long> tagIds) {
        events.publish(PostPublishedEvent.builder()
                .postId(post.getId())
                .authorId(post.getAuthorId())
                .title(post.getTitle())
                .content(post.getContent())
                .visibility(post.getVisibility())
                .postStatus(post.getPostStatus())
                .domain(post.getDomain())
                .timestamp(Instant.now().toEpochMilli())
                .tagIds(tagIds)
                .topicNotificationTargets(topicNotificationTargets(post, tagIds))
                .build());
    }

    private void evictPostDetailAfterCommit(Long postId) {
        afterCommit.execute(() -> {
            postDetailCache.evict(CacheKeyBuilder.postDetail(postId));
            postDetailCache.evict(CacheKeyBuilder.postDetailRaw(postId));
        }, "post review detail eviction:" + postId);
    }

    private List<com.offerlab.community.post.api.dto.TagDTO> tagsByIds(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        Map<Long, TagPO> tags = selectTagsByIds(tagIds).stream()
                .collect(Collectors.toMap(TagPO::getId, tag -> tag));
        return tagIds.stream()
                .map(tags::get)
                .filter(java.util.Objects::nonNull)
                .map(tag -> com.offerlab.community.post.api.dto.TagDTO.builder()
                        .id(tag.getId())
                        .name(tag.getTagName())
                        .tagType(tag.getTagType())
                        .useCount(tag.getUseCount())
                        .official(tag.getIsOfficial() != null && tag.getIsOfficial() == 1)
                        .build())
                .toList();
    }

    private void syncTags(Long postId, List<Long> tagIds) {
        Set<Long> oldIds = new LinkedHashSet<>(currentTagIds(postId));
        Set<Long> newIds = tagIds == null ? Set.of() : tagIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        postTagRefMapper.deleteByPostId(postId);
        oldIds.stream()
                .filter(id -> !newIds.contains(id))
                .forEach(postTagRefMapper::decrUseCount);
        for (Long tagId : newIds) {
            int inserted = postTagRefMapper.insertIgnore(idGen.nextId(), postId, tagId);
            if (inserted > 0 && !oldIds.contains(tagId)) {
                postTagRefMapper.incrUseCount(tagId);
            }
        }
    }

    private List<TagPO> selectTagsByIds(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        return migrationCheckService.tagGovernanceReady()
                ? tagMapper.selectActiveByIds(tagIds)
                : tagMapper.selectByIdsCompat(tagIds);
    }

    private List<PostTagView> selectTagsByPostIds(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return List.of();
        }
        return migrationCheckService.tagGovernanceReady()
                ? tagMapper.selectTagsByPostIds(postIds)
                : tagMapper.selectTagsByPostIdsCompat(postIds);
    }

    private List<TagPO> selectTagsByNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        return migrationCheckService.tagGovernanceReady()
                ? tagMapper.selectByNames(names)
                : tagMapper.selectByNamesCompat(names);
    }

    private void insertIgnoreName(Long id, String name, int tagType) {
        if (migrationCheckService.tagGovernanceReady()) {
            tagMapper.insertIgnoreName(id, name, tagType);
        } else {
            tagMapper.insertIgnoreNameCompat(id, name, tagType);
        }
    }

    private static void requireEditableStatus(Post post) {
        Integer status = post == null ? null : post.getPostStatus();
        if (!Objects.equals(status, Post.STATUS_PUBLISHED) && !Objects.equals(status, Post.STATUS_DRAFT)) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
    }

    private List<PostPublishedEvent.TopicNotificationTarget> topicNotificationTargets(Post post, List<Long> tagIds) {
        if (post == null || !Objects.equals(post.getVisibility(), Post.VIS_PUBLIC)
                || !Objects.equals(post.getPostStatus(), Post.STATUS_PUBLISHED)) {
            return List.of();
        }
        return communityTopicNotificationTargetService.targetsForPost(tagIds, post.getExtJson(), post.getAuthorId());
    }
}
