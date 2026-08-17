package com.offerlab.community.post.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.security.ExternalUrlSafety;
import com.offerlab.community.post.api.PublicContentFilter;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.CommunityTopicCmd;
import com.offerlab.community.post.api.dto.CommunityTopicDTO;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.PostCounterDTO;
import com.offerlab.community.post.api.dto.TagDTO;
import com.offerlab.community.post.infrastructure.persistence.mapper.CommunityTopicFollowMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.CommunityTopicMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.CommunityTopicTagMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostCounterMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostExtensionMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.TagMapper;
import com.offerlab.community.post.infrastructure.persistence.po.CommunityTopicFollowPO;
import com.offerlab.community.post.infrastructure.persistence.po.CommunityTopicPO;
import com.offerlab.community.post.infrastructure.persistence.po.CommunityTopicTagPO;
import com.offerlab.community.post.infrastructure.persistence.po.PostCounterPO;
import com.offerlab.community.post.infrastructure.persistence.po.PostExtensionPO;
import com.offerlab.community.post.infrastructure.persistence.po.PostPO;
import com.offerlab.community.post.infrastructure.persistence.po.TagPO;
import com.offerlab.community.post.infrastructure.persistence.projection.CommunityTopicFollowView;
import com.offerlab.community.post.infrastructure.persistence.projection.PostTagView;
import com.offerlab.community.user.api.UserFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommunityTopicService {

    private static final int MAX_LIMIT = 100;
    private static final int MAX_TOPIC_TAGS = 12;

    private final CommunityTopicMapper topicMapper;
    private final CommunityTopicFollowMapper topicFollowMapper;
    private final CommunityTopicTagMapper topicTagMapper;
    private final TagMapper tagMapper;
    private final PostMapper postMapper;
    private final PostExtensionMapper extensionMapper;
    private final PostCounterMapper counterMapper;
    private final UserFacade userFacade;
    private final SnowflakeIdGenerator idGen;
    private final AdminAuditService auditService;
    private final MigrationCheckService migrationCheckService;
    private final PostFacade postFacade;

    public List<CommunityTopicDTO> listPublic(Boolean featured, int limit, Long viewerUid) {
        return listPublic(featured, null, limit, viewerUid);
    }

    public List<CommunityTopicDTO> listPublic(Boolean featured, String keyword, int limit, Long viewerUid) {
        if (!topicSchemaReady()) {
            return List.of();
        }
        List<CommunityTopicPO> topics = topicMapper.selectTopics(true, featured, null, clean(keyword), safeLimit(limit));
        return toDtoList(topics, viewerUid);
    }

    public List<CommunityTopicDTO> listPublic(Boolean featured, int limit) {
        return listPublic(featured, limit, null);
    }

    public List<CommunityTopicDTO> listAdmin(Integer status, String keyword, int limit) {
        requireTopicSchemaReady();
        List<CommunityTopicPO> topics = topicMapper.selectTopics(false, null, status, clean(keyword), safeLimit(limit));
        return toDtoList(topics, null);
    }

    public CommunityTopicDTO getPublic(String slug, Long viewerUid) {
        TopicLookup lookup = resolveTopicForRead(slug);
        return publicTopicDto(lookup, viewerUid);
    }

    public CommunityTopicDTO resolvePublic(String slug, Long viewerUid) {
        TopicLookup lookup = findTopicForRead(slug);
        if (lookup == null || !lookup.virtualTopic() && !Objects.equals(lookup.topic().getTopicStatus(), 1)) {
            return null;
        }
        return publicTopicDto(lookup, viewerUid);
    }

    private CommunityTopicDTO publicTopicDto(TopicLookup lookup, Long viewerUid) {
        if (lookup.virtualTopic()) {
            return virtualTopicDto(lookup);
        }
        CommunityTopicPO topic = lookup.topic();
        if (!Objects.equals(topic.getTopicStatus(), 1)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return toDto(topic, lookup.tags(), viewerUid);
    }

    public CommunityTopicDTO getPublic(String slug) {
        return getPublic(slug, null);
    }

    public CommunityTopicDTO getAdmin(Long topicId) {
        requireTopicSchemaReady();
        CommunityTopicPO topic = requireTopic(topicId);
        return toDto(topic, topicTagMapper.selectTagsByTopicId(topic.getId()), null);
    }

    public PageResult<PostBriefDTO> listPosts(String slug, Integer postType, Boolean featured, long cursor, int size) {
        return listPosts(slug, postType, featured, cursor, size, null);
    }

    public PageResult<PostBriefDTO> listPosts(String slug, Integer postType, Boolean featured, long cursor, int size, Long viewerUid) {
        TopicLookup lookup = resolveTopicForRead(slug);
        if (!lookup.virtualTopic() && !Objects.equals(lookup.topic().getTopicStatus(), 1)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        int pageSize = Math.max(1, Math.min(size <= 0 ? 20 : size, MAX_LIMIT));
        List<Long> tagIds = lookup.tags().stream()
                .map(TagPO::getId)
                .filter(Objects::nonNull)
                .toList();
        List<PostPO> posts = postMapper.selectPublicPostsByTopic(lookup.topicId(), tagIds, lookup.keyword(),
                postType, featured, cursorTime(cursor), cursorId(cursor), pageSize + 1);
        return paged(posts, pageSize, viewerUid);
    }

    /*
     * Public topic URLs are sometimes opened from organic tech-stack names
     * such as /topics/java before operators have created a curated topic row.
     * Prefer the real topic when present; otherwise return a read-only
     * aggregate backed by tags and keyword search so the page does not look
     * broken to first-time community users.
     */
    private TopicLookup resolveTopicForRead(String slug) {
        TopicLookup lookup = findTopicForRead(slug);
        if (lookup == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return lookup;
    }

    private TopicLookup findTopicForRead(String slug) {
        String cleanSlug = slugOrDefault(slug, "");
        String displayName = topicDisplayName(cleanSlug);
        if (!topicSchemaReady()) {
            return supportedVirtualTopic(cleanSlug, displayName);
        }
        CommunityTopicPO topic = topicMapper.selectBySlug(cleanSlug);
        if (topic == null) {
            topic = topicMapper.selectBySlugOrName(cleanSlug, displayName);
        }
        if (topic != null) {
            List<TagPO> tags = topicTagMapper.selectTagsByTopicId(topic.getId());
            return new TopicLookup(topic, cleanSlug, topicKeyword(topic), tags, false);
        }
        return supportedVirtualTopic(cleanSlug, displayName);
    }

    private TopicLookup supportedVirtualTopic(String slug, String displayName) {
        return isSupportedVirtualTopicSlug(slug) ? virtualTopic(slug, displayName) : null;
    }

    private boolean isSupportedVirtualTopicSlug(String slug) {
        return switch (clean(slug).toLowerCase(Locale.ROOT)) {
            case "java", "jvm", "spring", "spring-boot", "spring-cloud",
                    "redis", "mysql", "mybatis", "kafka",
                    "elasticsearch", "elastic-search", "vue", "vue3",
                    "react", "docker", "kubernetes", "k8s" -> true;
            default -> false;
        };
    }

    @Transactional
    public CommunityTopicDTO follow(String slug, Long uid) {
        requireTopicSchemaReady();
        if (uid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        CommunityTopicPO topic = requireOnlineTopic(slug);
        try {
            CommunityTopicFollowPO existing = topicFollowMapper.selectAnyByPair(uid, topic.getId());
            if (existing == null) {
                CommunityTopicFollowPO follow = new CommunityTopicFollowPO();
                follow.setId(idGen.nextId());
                follow.setTopicId(topic.getId());
                follow.setUid(uid);
                follow.setIsDeleted(0);
                topicFollowMapper.insert(follow);
            } else if (!Objects.equals(existing.getIsDeleted(), 0)) {
                topicFollowMapper.restoreById(existing.getId());
            }
        } catch (DuplicateKeyException ignored) {
            // Another request already created the relation. Return the current topic state.
        }
        return toDto(topic, topicTagMapper.selectTagsByTopicId(topic.getId()), uid);
    }

    @Transactional
    public CommunityTopicDTO unfollow(String slug, Long uid) {
        requireTopicSchemaReady();
        if (uid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        CommunityTopicPO topic = requireOnlineTopic(slug);
        CommunityTopicFollowPO existing = topicFollowMapper.selectAnyByPair(uid, topic.getId());
        if (existing != null && Objects.equals(existing.getIsDeleted(), 0)) {
            topicFollowMapper.softDeleteById(existing.getId());
        }
        return toDto(topic, topicTagMapper.selectTagsByTopicId(topic.getId()), uid);
    }

    public CommunityTopicDTO followStatus(String slug, Long uid) {
        requireTopicSchemaReady();
        if (uid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        CommunityTopicPO topic = requireOnlineTopic(slug);
        return toDto(topic, topicTagMapper.selectTagsByTopicId(topic.getId()), uid);
    }

    public PageResult<CommunityTopicDTO> listFollowingTopics(Long uid, long cursor, int size) {
        if (!topicSchemaReady()) {
            return PageResult.empty();
        }
        if (uid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        int pageSize = Math.max(1, Math.min(size <= 0 ? 20 : size, MAX_LIMIT));
        List<CommunityTopicFollowView> rows = topicFollowMapper.selectFollowingTopics(uid, cursor <= 0 ? null : cursor, pageSize + 1);
        if (rows == null || rows.isEmpty()) {
            return PageResult.empty();
        }
        boolean hasMore = rows.size() > pageSize;
        List<CommunityTopicFollowView> pageRows = hasMore ? rows.subList(0, pageSize) : rows;
        List<CommunityTopicDTO> items = toDtoList(pageRows, uid);
        String next = hasMore && !pageRows.isEmpty()
                ? String.valueOf(pageRows.get(pageRows.size() - 1).getRelationId())
                : null;
        return PageResult.of(items, next, hasMore);
    }

    @Transactional
    public CommunityTopicDTO create(CommunityTopicCmd cmd, Long operatorUid) {
        requireTopicSchemaReady();
        if (operatorUid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        String name = requireText(cmd.getName(), "专题名称不能为空", 64);
        String slug = slugOrDefault(cmd.getSlug(), name);
        if (topicMapper.selectBySlug(slug) != null) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION.getCode(), "专题 slug 已存在");
        }
        CommunityTopicPO topic = new CommunityTopicPO();
        topic.setId(idGen.nextId());
        topic.setSlug(slug);
        topic.setTopicName(name);
        applyMutable(topic, cmd, operatorUid);
        topic.setCreatedBy(operatorUid);
        topic.setUpdatedBy(operatorUid);
        topic.setTopicStatus(normalizeStatus(cmd.getStatus(), 1));
        topicMapper.insert(topic);
        replaceTags(topic.getId(), cmd);
        auditService.recordRequired(operatorUid, "COMMUNITY_TOPIC_CREATE", "COMMUNITY_TOPIC", topic.getId(),
                null, toAudit(topic, cmd), cleanNote(cmd.getNote()));
        return getAdmin(topic.getId());
    }

    @Transactional
    public CommunityTopicDTO update(Long topicId, CommunityTopicCmd cmd, Long operatorUid) {
        requireTopicSchemaReady();
        if (operatorUid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        CommunityTopicPO topic = requireTopic(topicId);
        Map<String, Object> before = toAudit(topic, null);
        if (StringUtils.hasText(cmd.getName())) {
            topic.setTopicName(requireText(cmd.getName(), "专题名称不能为空", 64));
        }
        if (StringUtils.hasText(cmd.getSlug())) {
            String newSlug = slugOrDefault(cmd.getSlug(), topic.getTopicName());
            CommunityTopicPO existing = topicMapper.selectBySlug(newSlug);
            if (existing != null && !Objects.equals(existing.getId(), topicId)) {
                throw new BizException(ErrorCode.DUPLICATE_OPERATION.getCode(), "专题 slug 已存在");
            }
            topic.setSlug(newSlug);
        }
        applyMutable(topic, cmd, operatorUid);
        if (cmd.getStatus() != null) {
            topic.setTopicStatus(normalizeStatus(cmd.getStatus(), topic.getTopicStatus()));
        }
        topicMapper.updateById(topic);
        replaceTags(topicId, cmd);
        auditService.recordRequired(operatorUid, "COMMUNITY_TOPIC_UPDATE", "COMMUNITY_TOPIC", topicId,
                before, toAudit(topic, cmd), cleanNote(cmd.getNote()));
        return getAdmin(topicId);
    }

    @Transactional
    public CommunityTopicDTO updateStatus(Long topicId, Integer status, Long operatorUid, String note) {
        requireTopicSchemaReady();
        if (operatorUid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        CommunityTopicPO topic = requireTopic(topicId);
        Integer normalized = normalizeStatus(status, topic.getTopicStatus());
        topicMapper.updateStatus(topicId, normalized, operatorUid);
        auditService.recordRequired(operatorUid,
                normalized == 1 ? "COMMUNITY_TOPIC_ONLINE" : "COMMUNITY_TOPIC_OFFLINE",
                "COMMUNITY_TOPIC", topicId,
                Map.of("status", topic.getTopicStatus()),
                Map.of("status", normalized),
                cleanNote(note));
        return getAdmin(topicId);
    }

    private PageResult<PostBriefDTO> paged(List<PostPO> posts, int pageSize, Long viewerUid) {
        if (posts == null || posts.isEmpty()) {
            return PageResult.empty();
        }
        boolean hasMore = posts.size() > pageSize;
        List<PostPO> items = hasMore ? posts.subList(0, pageSize) : posts;
        List<Long> postIds = items.stream().map(PostPO::getId).toList();
        Map<Long, PostBriefDTO> visibleById = postFacade.batchGetPosts(postIds, viewerUid);
        List<PostBriefDTO> briefs = postIds.stream()
                .map(visibleById::get)
                .filter(Objects::nonNull)
                .filter(post -> !PublicContentFilter.isSyntheticPost(post))
                .toList();
        PostPO cursorPost = hasMore && !items.isEmpty() ? items.get(items.size() - 1) : null;
        String nextCursor = cursorPost == null ? null : listCursor(cursorPost.getCreateTime(), cursorPost.getId());
        return PageResult.of(briefs, nextCursor, hasMore);
    }

    private List<PostBriefDTO> toBriefs(List<PostPO> posts) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        List<Long> postIds = posts.stream().map(PostPO::getId).toList();
        Map<Long, String> extJson = extensionMapper.selectBatchIds(postIds).stream()
                .collect(Collectors.toMap(PostExtensionPO::getPostId, PostExtensionPO::getExtJson, (left, right) -> left));
        Map<Long, List<TagDTO>> tags = tagsByPostIds(postIds);
        Map<Long, PostCounterDTO> counters = countersByPostIds(postIds);
        Map<Long, com.offerlab.community.user.api.dto.UserBriefDTO> authors = userFacade.batchGetUserBriefs(
                posts.stream().map(PostPO::getAuthorId).filter(Objects::nonNull).collect(Collectors.toSet()));
        return posts.stream()
                .map(post -> PostBriefDTO.builder()
                        .id(post.getId())
                        .authorId(post.getAuthorId())
                        .author(authors.get(post.getAuthorId()))
                        .postType(post.getPostType())
                        .title(post.getTitle())
                        .summary(summary(post.getContent()))
                        .coverUrl(post.getCoverUrl())
                        .extJson(extJson.get(post.getId()))
                        .tags(tags.getOrDefault(post.getId(), List.of()))
                        .counter(counters.getOrDefault(post.getId(), emptyCounter(post.getId())))
                        .createTime(post.getCreateTime())
                        .build())
                .toList();
    }

    private List<CommunityTopicDTO> toDtoList(List<? extends CommunityTopicPO> topics, Long viewerUid) {
        if (topics == null || topics.isEmpty()) {
            return List.of();
        }
        List<Long> topicIds = topics.stream()
                .map(CommunityTopicPO::getId)
                .filter(Objects::nonNull)
                .toList();
        Set<Long> followedTopicIds = followedTopicIds(viewerUid, topics);
        Map<Long, List<TagPO>> tagsByTopicId = tagsByTopicIds(topicIds);
        Map<Long, Long> postCounts = postCountsByTopicIds(topicIds);
        Map<Long, Long> followerCounts = followerCountsByTopicIds(topicIds);
        return topics.stream()
                .map(topic -> toDto(topic,
                        tagsByTopicId.getOrDefault(topic.getId(), List.of()),
                        followedTopicIds.contains(topic.getId()),
                        postCounts.getOrDefault(topic.getId(), 0L),
                        followerCounts.getOrDefault(topic.getId(), 0L)))
                .toList();
    }

    private CommunityTopicDTO toDto(CommunityTopicPO topic, List<TagPO> tags, Long viewerUid) {
        return toDto(topic, tags, isFollowingTopic(viewerUid, topic.getId()));
    }

    private CommunityTopicDTO toDto(CommunityTopicPO topic, List<TagPO> tags, boolean followed) {
        TopicStatistics statistics = topicStatistics(
                topic.getId(),
                tags == null ? List.of() : tags.stream().map(TagPO::getId).filter(Objects::nonNull).toList(),
                topicKeyword(topic));
        return toDto(topic, tags, followed, statistics, topicFollowMapper.countByTopicId(topic.getId()));
    }

    private CommunityTopicDTO toDto(CommunityTopicPO topic, List<TagPO> tags, boolean followed,
                                    long postCount, long followerCount) {
        return toDto(topic, tags, followed,
                new TopicStatistics(postCount, Map.of(), false), followerCount);
    }

    private CommunityTopicDTO toDto(CommunityTopicPO topic, List<TagPO> tags, boolean followed,
                                    TopicStatistics statistics, long followerCount) {
        List<TagDTO> tagDtos = tags == null ? List.of() : tags.stream().map(this::toTagDto).toList();
        return CommunityTopicDTO.builder()
                .id(topic.getId())
                .slug(topic.getSlug())
                .name(topic.getTopicName())
                .description(topic.getDescription())
                .topicType(topic.getTopicType())
                .coverUrl(topic.getCoverUrl())
                .sortOrder(topic.getSortOrder())
                .featured(topic.getFeatured() != null && topic.getFeatured() == 1)
                .status(topic.getTopicStatus())
                .postCount(statistics.postCount())
                .typeDistribution(statistics.typeDistribution())
                .statisticsAvailable(statistics.available())
                .followerCount(followerCount)
                .followed(followed)
                .virtualTopic(false)
                .tags(tagDtos)
                .createTime(topic.getCreateTime())
                .updateTime(topic.getUpdateTime())
                .build();
    }

    private TopicLookup virtualTopic(String slug, String displayName) {
        List<String> names = virtualTopicTagNames(slug, displayName);
        List<TagPO> tags = names.isEmpty() ? List.of() : selectTagsByNames(names);
        String keyword = tags.stream()
                .map(TagPO::getTagName)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(displayName);
        return new TopicLookup(null, slug, keyword, tags, true);
    }

    private CommunityTopicDTO virtualTopicDto(TopicLookup lookup) {
        TopicStatistics statistics = topicStatistics(
                lookup.topicId(),
                lookup.tags().stream().map(TagPO::getId).filter(Objects::nonNull).toList(),
                lookup.keyword());
        return CommunityTopicDTO.builder()
                .id(null)
                .slug(lookup.slug())
                .name(lookup.keyword())
                .description("按技术栈、主题标签和正文关键词自动聚合的社区内容。")
                .topicType("tech_stack")
                .featured(false)
                .status(1)
                .postCount(statistics.postCount())
                .typeDistribution(statistics.typeDistribution())
                .statisticsAvailable(statistics.available())
                .followerCount(0L)
                .followed(false)
                .virtualTopic(true)
                .tags(lookup.tags().stream().map(this::toTagDto).toList())
                .build();
    }

    private TopicStatistics topicStatistics(Long topicId, List<Long> tagIds, String keyword) {
        try {
            long postCount = postMapper.countPublicPostsByTopic(topicId, tagIds, keyword);
            List<Map<String, Object>> rows = postMapper.countPublicPostTypesByTopic(topicId, tagIds, keyword);
            Map<String, Long> distribution = new java.util.LinkedHashMap<>();
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    Long type = asLong(row, "type", "TYPE", "postType", "post_type");
                    Long count = asLong(row, "count", "COUNT");
                    if (type != null && count != null && count > 0) {
                        distribution.put(String.valueOf(type), count);
                    }
                }
            }
            return new TopicStatistics(postCount, Map.copyOf(distribution), true);
        } catch (RuntimeException ex) {
            log.warn("public topic statistics unavailable, topicId={}, keyword={}", topicId, keyword, ex);
            return new TopicStatistics(0L, Map.of(), false);
        }
    }

    private List<String> virtualTopicTagNames(String slug, String displayName) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (StringUtils.hasText(displayName)) {
            names.add(displayName);
        }
        if (StringUtils.hasText(slug)) {
            names.add(slug);
            names.add(slug.toUpperCase(Locale.ROOT));
            names.add(slug.replace("-", " "));
        }
        return names.stream()
                .filter(StringUtils::hasText)
                .limit(MAX_TOPIC_TAGS)
                .toList();
    }

    private String topicDisplayName(String slug) {
        String cleanSlug = clean(slug).toLowerCase(Locale.ROOT);
        return switch (cleanSlug) {
            case "java" -> "Java";
            case "jvm" -> "JVM";
            case "spring", "spring-boot" -> "Spring Boot";
            case "spring-cloud" -> "Spring Cloud";
            case "redis" -> "Redis";
            case "mysql" -> "MySQL";
            case "mybatis" -> "MyBatis";
            case "kafka" -> "Kafka";
            case "elasticsearch", "elastic-search" -> "Elasticsearch";
            case "vue", "vue3" -> "Vue";
            case "react" -> "React";
            case "docker" -> "Docker";
            case "kubernetes", "k8s" -> "Kubernetes";
            default -> titleizeSlug(cleanSlug);
        };
    }

    private String titleizeSlug(String slug) {
        String text = clean(slug).replace('-', ' ');
        if (!StringUtils.hasText(text)) {
            return "专题";
        }
        String[] parts = text.split("\\s+");
        List<String> words = new ArrayList<>();
        for (String part : parts) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            if (part.length() == 1) {
                words.add(part.toUpperCase(Locale.ROOT));
            } else if (part.chars().allMatch(ch -> ch < 128)) {
                words.add(part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1));
            } else {
                words.add(part);
            }
        }
        return String.join(" ", words);
    }

    private Set<Long> followedTopicIds(Long viewerUid, List<? extends CommunityTopicPO> topics) {
        if (viewerUid == null || topics == null || topics.isEmpty()) {
            return Set.of();
        }
        List<Long> topicIds = topics.stream().map(CommunityTopicPO::getId).filter(Objects::nonNull).toList();
        if (topicIds.isEmpty()) {
            return Set.of();
        }
        return new java.util.HashSet<>(topicFollowMapper.selectFollowedTopicIds(viewerUid, topicIds));
    }

    private Map<Long, List<TagPO>> tagsByTopicIds(Collection<Long> topicIds) {
        if (topicIds == null || topicIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<TagPO>> result = new HashMap<>();
        List<Map<String, Object>> rows = topicTagMapper.selectTagsByTopicIds(topicIds);
        if (rows == null || rows.isEmpty()) {
            return result;
        }
        for (Map<String, Object> row : rows) {
            Long topicId = asLong(row, "topicId", "topic_id", "TOPICID", "TOPIC_ID");
            Long tagId = asLong(row, "id", "ID");
            if (topicId == null || tagId == null) {
                continue;
            }
            TagPO tag = new TagPO();
            tag.setId(tagId);
            tag.setTagName(asString(row, "tagName", "tag_name", "TAGNAME", "TAG_NAME"));
            tag.setTagType(asInteger(row, "tagType", "tag_type", "TAGTYPE", "TAG_TYPE"));
            tag.setUseCount(defaultLong(asLong(row, "useCount", "use_count", "USECOUNT", "USE_COUNT")));
            tag.setIsOfficial(asInteger(row, "isOfficial", "is_official", "ISOFFICIAL", "IS_OFFICIAL"));
            tag.setTagStatus(asInteger(row, "tagStatus", "tag_status", "TAGSTATUS", "TAG_STATUS"));
            tag.setRecommended(asInteger(row, "recommended", "RECOMMENDED"));
            tag.setSynonyms(asString(row, "synonyms", "SYNONYMS"));
            tag.setMergeTargetId(asLong(row, "mergeTargetId", "merge_target_id", "MERGETARGETID", "MERGE_TARGET_ID"));
            result.computeIfAbsent(topicId, ignored -> new ArrayList<>()).add(tag);
        }
        return result;
    }

    private Map<Long, Long> postCountsByTopicIds(Collection<Long> topicIds) {
        if (topicIds == null || topicIds.isEmpty()) {
            return Map.of();
        }
        return metricByTopicId(topicMapper.countPublicPostsByTopicIds(topicIds),
                "postCount", "post_count", "POSTCOUNT", "POST_COUNT");
    }

    private Map<Long, Long> followerCountsByTopicIds(Collection<Long> topicIds) {
        if (topicIds == null || topicIds.isEmpty()) {
            return Map.of();
        }
        return metricByTopicId(topicMapper.countFollowersByTopicIds(topicIds),
                "followerCount", "follower_count", "FOLLOWERCOUNT", "FOLLOWER_COUNT");
    }

    private Map<Long, Long> metricByTopicId(List<Map<String, Object>> rows, String... metricKeys) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long topicId = asLong(row, "topicId", "topic_id", "TOPICID", "TOPIC_ID");
            if (topicId != null) {
                result.put(topicId, defaultLong(asLong(row, metricKeys)));
            }
        }
        return result;
    }

    private Long asLong(Map<String, Object> row, String... keys) {
        Object value = value(row, keys);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        return null;
    }

    private Integer asInteger(Map<String, Object> row, String... keys) {
        Object value = value(row, keys);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return null;
    }

    private String asString(Map<String, Object> row, String... keys) {
        Object value = value(row, keys);
        return value == null ? null : String.valueOf(value);
    }

    private Object value(Map<String, Object> row, String... keys) {
        if (row == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (row.containsKey(key)) {
                return row.get(key);
            }
        }
        return null;
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private boolean isFollowingTopic(Long viewerUid, Long topicId) {
        if (viewerUid == null || topicId == null) {
            return false;
        }
        CommunityTopicFollowPO follow = topicFollowMapper.selectAnyByPair(viewerUid, topicId);
        return follow != null && Objects.equals(follow.getIsDeleted(), 0);
    }

    private void applyMutable(CommunityTopicPO topic, CommunityTopicCmd cmd, Long operatorUid) {
        if (cmd == null) {
            return;
        }
        if (cmd.getDescription() != null) {
            topic.setDescription(limit(cmd.getDescription(), 500));
        }
        if (cmd.getTopicType() != null) {
            topic.setTopicType(limit(clean(cmd.getTopicType()), 32));
        } else if (!StringUtils.hasText(topic.getTopicType())) {
            topic.setTopicType("custom");
        }
        if (cmd.getCoverUrl() != null) {
            topic.setCoverUrl(normalizeCoverUrl(cmd.getCoverUrl()));
        }
        if (cmd.getSortOrder() != null) {
            topic.setSortOrder(Math.max(0, Math.min(cmd.getSortOrder(), 9999)));
        } else if (topic.getSortOrder() == null) {
            topic.setSortOrder(0);
        }
        if (cmd.getFeatured() != null) {
            topic.setFeatured(Boolean.TRUE.equals(cmd.getFeatured()) ? 1 : 0);
        } else if (topic.getFeatured() == null) {
            topic.setFeatured(0);
        }
        topic.setUpdatedBy(operatorUid);
    }

    private void replaceTags(Long topicId, CommunityTopicCmd cmd) {
        if (cmd == null && topicId != null) {
            return;
        }
        if (cmd.getTagIds() == null && cmd.getTagNames() == null) {
            return;
        }
        topicTagMapper.deleteByTopicId(topicId);
        List<Long> tagIds = resolveTagIds(cmd);
        for (Long tagId : tagIds) {
            CommunityTopicTagPO ref = new CommunityTopicTagPO();
            ref.setId(idGen.nextId());
            ref.setTopicId(topicId);
            ref.setTagId(tagId);
            topicTagMapper.insert(ref);
        }
    }

    private List<Long> resolveTagIds(CommunityTopicCmd cmd) {
        Set<Long> ids = new LinkedHashSet<>();
        if (cmd.getTagIds() != null) {
            cmd.getTagIds().stream()
                    .filter(id -> id != null && id > 0)
                    .limit(MAX_TOPIC_TAGS)
                    .forEach(ids::add);
        }
        List<String> names = normalizeTagNames(cmd.getTagNames());
        if (!names.isEmpty()) {
            Map<String, TagPO> existing = selectTagsByNames(names).stream()
                    .collect(Collectors.toMap(TagPO::getTagName, tag -> tag, (left, right) -> left));
            for (String name : names) {
                TagPO tag = existing.get(name);
                if (tag == null) {
                    Long tagId = idGen.nextId();
                    insertIgnoreName(tagId, name, 1);
                    tag = selectTagsByNames(List.of(name)).stream().findFirst().orElse(null);
                    if (tag == null) {
                        ids.add(tagId);
                        continue;
                    }
                }
                ids.add(tag.getId());
                if (ids.size() >= MAX_TOPIC_TAGS) {
                    break;
                }
            }
        }
        if (!ids.isEmpty()) {
            Set<Long> activeIds = selectTagsByIds(ids).stream()
                    .filter(tag -> !Objects.equals(tag.getTagStatus(), 0))
                    .map(TagPO::getId)
                    .collect(Collectors.toSet());
            if (activeIds.size() != ids.size()) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "专题不能绑定已禁用标签");
            }
        }
        return new ArrayList<>(ids).stream().limit(MAX_TOPIC_TAGS).toList();
    }

    private List<String> normalizeTagNames(List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) {
            return List.of();
        }
        return tagNames.stream()
                .map(this::clean)
                .filter(StringUtils::hasText)
                .map(name -> limit(name, 64))
                .distinct()
                .limit(MAX_TOPIC_TAGS)
                .toList();
    }

    private CommunityTopicPO requireTopicBySlug(String slug) {
        String cleanSlug = slugOrDefault(slug, "");
        CommunityTopicPO topic = topicMapper.selectBySlug(cleanSlug);
        if (topic == null) {
            topic = topicMapper.selectBySlugOrName(cleanSlug, topicDisplayName(cleanSlug));
        }
        if (topic == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return topic;
    }

    private CommunityTopicPO requireOnlineTopic(String slug) {
        CommunityTopicPO topic = requireTopicBySlug(slug);
        if (!Objects.equals(topic.getTopicStatus(), 1)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return topic;
    }

    private CommunityTopicPO requireTopic(Long topicId) {
        if (topicId == null || topicId <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        CommunityTopicPO topic = topicMapper.selectById(topicId);
        if (topic == null || Objects.equals(topic.getIsDeleted(), 1)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return topic;
    }

    private Map<Long, List<TagDTO>> tagsByPostIds(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        return selectTagsByPostIds(postIds).stream()
                .collect(Collectors.groupingBy(PostTagView::getPostId,
                        Collectors.mapping(this::toPostTagDto, Collectors.toList())));
    }

    private Map<Long, PostCounterDTO> countersByPostIds(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, PostCounterDTO> result = new HashMap<>();
        for (PostCounterPO counter : counterMapper.selectBatchIds(postIds)) {
            result.put(counter.getPostId(), PostCounterDTO.builder()
                    .postId(counter.getPostId())
                    .viewCount(counter.getViewCount())
                    .likeCount(counter.getLikeCount())
                    .commentCount(counter.getCommentCount())
                    .favoriteCount(counter.getFavoriteCount())
                    .build());
        }
        return result;
    }

    private TagDTO toTagDto(TagPO tag) {
        return TagDTO.builder()
                .id(tag.getId())
                .name(tag.getTagName())
                .slug("tag-" + tag.getId())
                .category(tag.getTagType() != null && tag.getTagType() == 1 ? "tech" : "custom")
                .tagType(tag.getTagType())
                .useCount(tag.getUseCount())
                .official(tag.getIsOfficial() != null && tag.getIsOfficial() == 1)
                .status(tag.getTagStatus() == null ? 1 : tag.getTagStatus())
                .recommended(tag.getRecommended() != null && tag.getRecommended() == 1)
                .mergeTargetId(tag.getMergeTargetId())
                .synonyms(parseSynonyms(tag.getSynonyms()))
                .build();
    }

    private TagDTO toPostTagDto(PostTagView tag) {
        return TagDTO.builder()
                .id(tag.getId())
                .name(tag.getTagName())
                .slug("tag-" + tag.getId())
                .category(tag.getTagType() != null && tag.getTagType() == 1 ? "tech" : "custom")
                .tagType(tag.getTagType())
                .useCount(tag.getUseCount())
                .official(tag.getIsOfficial() != null && tag.getIsOfficial() == 1)
                .status(tag.getTagStatus() == null ? 1 : tag.getTagStatus())
                .recommended(tag.getRecommended() != null && tag.getRecommended() == 1)
                .mergeTargetId(tag.getMergeTargetId())
                .synonyms(parseSynonyms(tag.getSynonyms()))
                .build();
    }

    private List<String> parseSynonyms(String synonyms) {
        if (!StringUtils.hasText(synonyms)) {
            return List.of();
        }
        return java.util.Arrays.stream(synonyms.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<TagPO> selectTagsByNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        return migrationCheckService.tagGovernanceReady()
                ? tagMapper.selectByNames(names)
                : tagMapper.selectByNamesCompat(names);
    }

    private List<TagPO> selectTagsByIds(Collection<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        return migrationCheckService.tagGovernanceReady()
                ? tagMapper.selectActiveByIds(tagIds)
                : tagMapper.selectByIdsCompat(tagIds);
    }

    private void insertIgnoreName(Long id, String name, int tagType) {
        if (migrationCheckService.tagGovernanceReady()) {
            tagMapper.insertIgnoreName(id, name, tagType);
        } else {
            tagMapper.insertIgnoreNameCompat(id, name, tagType);
        }
    }

    private List<PostTagView> selectTagsByPostIds(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return List.of();
        }
        return migrationCheckService.tagGovernanceReady()
                ? tagMapper.selectTagsByPostIds(postIds)
                : tagMapper.selectTagsByPostIdsCompat(postIds);
    }

    private boolean topicSchemaReady() {
        return migrationCheckService.communityTopicReady();
    }

    private void requireTopicSchemaReady() {
        if (!topicSchemaReady()) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                    "社区专题数据库迁移未完成，请先执行 db/migration/20260608_community_topics.sql");
        }
    }

    private Map<String, Object> toAudit(CommunityTopicPO topic, CommunityTopicCmd cmd) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", topic.getId());
        data.put("slug", topic.getSlug());
        data.put("name", topic.getTopicName());
        data.put("type", topic.getTopicType());
        data.put("featured", topic.getFeatured());
        data.put("status", topic.getTopicStatus());
        data.put("sortOrder", topic.getSortOrder());
        if (cmd != null) {
            data.put("tagIds", cmd.getTagIds());
            data.put("tagNames", cmd.getTagNames());
        }
        return data;
    }

    private String topicKeyword(CommunityTopicPO topic) {
        if (topic == null) {
            return "";
        }
        if (StringUtils.hasText(topic.getTopicName())) {
            return topic.getTopicName().trim();
        }
        return topic.getSlug();
    }

    private Integer normalizeStatus(Integer status, Integer fallback) {
        int value = status == null ? (fallback == null ? 1 : fallback) : status;
        return value == 1 ? 1 : 0;
    }

    private record TopicLookup(CommunityTopicPO topic, String slug, String keyword, List<TagPO> tags, boolean virtualTopic) {
        private Long topicId() {
            return topic == null ? null : topic.getId();
        }
    }

    private record TopicStatistics(long postCount, Map<String, Long> typeDistribution, boolean available) {
    }

    private int safeLimit(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 20 : limit, MAX_LIMIT));
    }

    private String requireText(String value, String message, int max) {
        String text = clean(value);
        if (!StringUtils.hasText(text)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), message);
        }
        return limit(text, max);
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String cleanNote(String note) {
        String text = clean(note);
        return StringUtils.hasText(text) ? limit(text, 500) : null;
    }

    private String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private String normalizeCoverUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return ExternalUrlSafety.requireSafeHttpUrl(value, "coverUrl", 512);
    }

    private String slugOrDefault(String slug, String fallback) {
        String source = StringUtils.hasText(slug) ? slug : fallback;
        String normalized = Normalizer.normalize(source == null ? "" : source, Normalizer.Form.NFKC)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (!StringUtils.hasText(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "专题 slug 不能为空");
        }
        return limit(normalized, 64);
    }

    private static String summary(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120) + "...";
    }

    private static PostCounterDTO emptyCounter(Long postId) {
        return PostCounterDTO.builder()
                .postId(postId)
                .viewCount(0L)
                .likeCount(0L)
                .commentCount(0L)
                .favoriteCount(0L)
                .build();
    }

    private static LocalDateTime cursorTime(long cursor) {
        if (cursor <= 0) {
            return null;
        }
        long time = cursor > 10_000_000_000_000L ? cursor / 1_000_000L : cursor;
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneOffset.UTC);
    }

    private static Long cursorId(long cursor) {
        if (cursor <= 0 || cursor <= 10_000_000_000_000L) {
            return Long.MAX_VALUE;
        }
        return cursor % 1_000_000L;
    }

    private static String listCursor(LocalDateTime time, Long id) {
        if (time == null) {
            return null;
        }
        long millis = time.toInstant(ZoneOffset.UTC).toEpochMilli();
        long safeId = id == null ? 0 : Math.abs(id % 1_000_000L);
        return String.valueOf(millis * 1_000_000L + safeId);
    }
}
