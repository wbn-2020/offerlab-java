package com.offerlab.community.post.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.post.api.dto.CommunityTopicDTO;
import com.offerlab.community.post.api.dto.DiscoveryMapDTO;
import com.offerlab.community.post.api.dto.OperationSlotDTO;
import com.offerlab.community.post.api.dto.OperationSlotItemDTO;
import com.offerlab.community.post.api.dto.OperationTopicDTO;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DiscoveryMapService {
    public static final String SOURCE_OPERATION_CURATION = "operation-curation";
    public static final String SOURCE_COMMUNITY_TOPIC = "community-topic";
    public static final String SOURCE_SEARCH_ANALYTICS = "search-analytics";
    public static final String SOURCE_PUBLIC_CONTENT_QUERY = "public-content-query";
    public static final String SOURCE_FALLBACK_DEMO = "fallback-demo";
    public static final String SOURCE_UNAVAILABLE = "unavailable";

    private static final List<String> FORBIDDEN_DISCOVERY_TEXT = List.of(
            "CodeCoachAI",
            "mockInterview",
            "resumeMatch",
            "privateGoal",
            "applicationTask",
            "AI 教练",
            "私人训练",
            "训练计划",
            "模拟面试",
            "简历匹配",
            "简历/JD",
            "JD 分析",
            "投递任务"
    );
    private static final String STATUS_READY = "READY";
    private static final String STATUS_EMPTY = "EMPTY";
    private static final String STATUS_UNAVAILABLE = "UNAVAILABLE";
    private static final int DEFAULT_FEATURED_LIMIT = 5;
    private static final int DEFAULT_TOPIC_LIMIT = 8;
    private static final int DEFAULT_SEARCH_LIMIT = 6;

    private final OperationCurationService operationCurationService;
    private final CommunityTopicService communityTopicService;

    public DiscoveryMapDTO getPublicMap(int featuredLimit, int topicLimit) {
        List<DiscoveryMapDTO.DiscoveryItemDTO> featuredTopics = loadFeaturedTopics(featuredLimit);
        List<DiscoveryMapDTO.DiscoveryItemDTO> channels = channelEntrypoints();
        List<DiscoveryMapDTO.DiscoveryItemDTO> activeTopics = loadActiveTopics(topicLimit);
        List<DiscoveryMapDTO.DiscoveryItemDTO> searchEntrypoints = searchEntrypoints();

        Map<String, DiscoveryMapDTO.DiscoveryModuleDTO> modules = new LinkedHashMap<>();
        modules.put("featuredTopics", module("featuredTopics", "精选专题", SOURCE_OPERATION_CURATION,
                featuredTopics, "discovery_featured_topics_empty"));
        modules.put("channels", module("channels", "频道入口", SOURCE_PUBLIC_CONTENT_QUERY,
                channels, null));
        modules.put("activeTopics", module("activeTopics", "活跃话题", SOURCE_COMMUNITY_TOPIC,
                activeTopics, "community_topics_empty"));
        modules.put("searchEntrypoints", module("searchEntrypoints", "搜索延展", SOURCE_SEARCH_ANALYTICS,
                searchEntrypoints, null));

        boolean degraded = modules.values().stream().anyMatch(item -> Boolean.TRUE.equals(item.getDegraded()));
        String fallbackReason = modules.values().stream()
                .map(DiscoveryMapDTO.DiscoveryModuleDTO::getFallbackReason)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
        return DiscoveryMapDTO.builder()
                .source(degraded ? SOURCE_PUBLIC_CONTENT_QUERY : SOURCE_OPERATION_CURATION)
                .degraded(degraded)
                .fallbackReason(fallbackReason)
                .generatedAt(LocalDateTime.now())
                .modules(modules)
                .featuredTopics(featuredTopics)
                .channels(channels)
                .activeTopics(activeTopics)
                .searchEntrypoints(searchEntrypoints)
                .build();
    }

    private List<DiscoveryMapDTO.DiscoveryItemDTO> loadFeaturedTopics(int limit) {
        try {
            OperationSlotDTO slot = operationCurationService.getPublicSlot(
                    OperationCurationService.DISCOVERY_FEATURED_TOPICS_SLOT_CODE,
                    safeLimit(limit, DEFAULT_FEATURED_LIMIT, DEFAULT_FEATURED_LIMIT)
            );
            return slot.getItems() == null ? List.of() : slot.getItems().stream()
                    .map(this::slotItem)
                    .filter(Objects::nonNull)
                    .filter(DiscoveryMapService::isDisplayableItem)
                    .limit(DEFAULT_FEATURED_LIMIT)
                    .toList();
        } catch (BizException e) {
            return List.of();
        }
    }

    private List<DiscoveryMapDTO.DiscoveryItemDTO> loadActiveTopics(int limit) {
        try {
            return communityTopicService.listPublic(null, safeLimit(limit, DEFAULT_TOPIC_LIMIT, DEFAULT_TOPIC_LIMIT)).stream()
                    .filter(topic -> StringUtils.hasText(topic.getSlug()))
                    .map(this::communityTopic)
                    .filter(DiscoveryMapService::isDisplayableItem)
                    .limit(DEFAULT_TOPIC_LIMIT)
                    .toList();
        } catch (BizException e) {
            return List.of();
        }
    }

    private DiscoveryMapDTO.DiscoveryItemDTO slotItem(OperationSlotItemDTO item) {
        if (item == null || Boolean.TRUE.equals(item.getBlocked())) {
            return null;
        }
        if (OperationCurationService.SOURCE_OPERATION_TOPIC.equals(item.getSourceType())) {
            OperationTopicDTO topic = item.getTopic();
            if (topic == null || !StringUtils.hasText(topic.getSlug())) {
                return null;
            }
            return DiscoveryMapDTO.DiscoveryItemDTO.builder()
                    .id("operation-topic:" + topic.getId())
                    .type("operation-topic")
                    .title(topic.getName())
                    .summary(topic.getDescription())
                    .href("/topics/" + topic.getSlug())
                    .source(SOURCE_OPERATION_CURATION)
                    .slug(topic.getSlug())
                    .sourceId(topic.getId())
                    .reason(item.getReasonText())
                    .reasonText(item.getReasonText())
                    .build();
        }
        PostBriefDTO post = item.getPost();
        if (post == null || post.getId() == null) {
            return null;
        }
        return DiscoveryMapDTO.DiscoveryItemDTO.builder()
                .id("post:" + post.getId())
                .type("post")
                .title(post.getTitle())
                .summary(post.getSummary())
                .href("/post/" + post.getId())
                .source(SOURCE_OPERATION_CURATION)
                .sourceId(post.getId())
                .domain(post.getDomain())
                .reason(item.getReasonText())
                .reasonText(item.getReasonText())
                .build();
    }

    private DiscoveryMapDTO.DiscoveryItemDTO communityTopic(CommunityTopicDTO topic) {
        return DiscoveryMapDTO.DiscoveryItemDTO.builder()
                .id("community-topic:" + topic.getId())
                .type("community-topic")
                .title(topic.getName())
                .summary(topic.getDescription())
                .href("/topics/" + topic.getSlug())
                .source(SOURCE_COMMUNITY_TOPIC)
                .slug(topic.getSlug())
                .sourceId(topic.getId())
                .tags(topic.getTags() == null ? List.of() : topic.getTags().stream()
                        .map(tag -> tag == null ? null : tag.getName())
                        .filter(StringUtils::hasText)
                        .limit(4)
                        .toList())
                .reason(topic.getPostCount() == null ? null : topic.getPostCount() + " public posts")
                .reasonText(topic.getPostCount() == null ? null : topic.getPostCount() + " public posts")
                .build();
    }

    private List<DiscoveryMapDTO.DiscoveryItemDTO> channelEntrypoints() {
        return List.of(
                channel("tech-digital", "科技数码", "编程、AI 工具、产品体验和效率工具。", "/search?domain=1&sort=hot", "1"),
                channel("career-experience", "职场经验", "求职经验、工作复盘和职场选择。", "/search?domain=2&sort=hot", "2"),
                channel("learning-growth", "学习成长", "学习方法、读书笔记和技能提升。", "/search?domain=3&sort=hot", "3"),
                channel("lifestyle", "生活方式", "租房、城市生活、消费经验和健康日常。", "/search?domain=4&sort=hot", "4"),
                channel("resources", "资源推荐", "工具、书单、课程、模板和资料合集。", "/search?type=4&sort=hot", "R"),
                channel("qa-discussion", "问答讨论", "求建议、观点讨论和经验征集。", "/search?type=2&sort=hot", "Q")
        );
    }

    private DiscoveryMapDTO.DiscoveryItemDTO channel(String key, String title, String summary, String href, String icon) {
        return DiscoveryMapDTO.DiscoveryItemDTO.builder()
                .id("channel:" + key)
                .type("channel")
                .title(title)
                .summary(summary)
                .href(href)
                .source(SOURCE_PUBLIC_CONTENT_QUERY)
                .icon(icon)
                .build();
    }

    private List<DiscoveryMapDTO.DiscoveryItemDTO> searchEntrypoints() {
        return List.of(
                search("hot", "热门内容", "按热度浏览最近公开讨论。", "/search?sort=hot"),
                search("topics", "找话题", "进入话题搜索，继续延展同主题内容。", "/search?mode=topics"),
                search("posts", "搜经验", "搜索公开经验、复盘和问答。", "/search?mode=posts"),
                search("career", "求职经验", "浏览公开求职、面试和职场复盘。", "/search?q=%E6%B1%82%E8%81%8C%E7%BB%8F%E9%AA%8C&sort=hot"),
                search("learning", "学习方法", "查看学习、读书和技能成长内容。", "/search?q=%E5%AD%A6%E4%B9%A0%E6%96%B9%E6%B3%95&sort=hot"),
                search("tools", "工具推荐", "发现公开工具体验和资源整理。", "/search?q=%E5%B7%A5%E5%85%B7%E6%8E%A8%E8%8D%90&sort=hot")
        ).stream().limit(DEFAULT_SEARCH_LIMIT).toList();
    }

    private DiscoveryMapDTO.DiscoveryItemDTO search(String key, String title, String summary, String href) {
        return DiscoveryMapDTO.DiscoveryItemDTO.builder()
                .id("search:" + key)
                .type("search")
                .title(title)
                .summary(summary)
                .href(href)
                .source(SOURCE_SEARCH_ANALYTICS)
                .build();
    }

    private DiscoveryMapDTO.DiscoveryModuleDTO module(
            String key,
            String title,
            String source,
            List<DiscoveryMapDTO.DiscoveryItemDTO> items,
            String emptyReason
    ) {
        int count = items == null ? 0 : items.size();
        boolean empty = count == 0;
        return DiscoveryMapDTO.DiscoveryModuleDTO.builder()
                .key(key)
                .title(title)
                .status(empty ? STATUS_EMPTY : STATUS_READY)
                .source(empty ? SOURCE_UNAVAILABLE : source)
                .degraded(empty)
                .fallbackReason(empty ? emptyReason : null)
                .itemCount(count)
                .build();
    }

    private static int safeLimit(int limit, int fallback, int max) {
        return Math.max(1, Math.min(limit <= 0 ? fallback : limit, max));
    }

    private static boolean isDisplayableSource(String source) {
        return SOURCE_OPERATION_CURATION.equals(source)
                || SOURCE_COMMUNITY_TOPIC.equals(source)
                || SOURCE_SEARCH_ANALYTICS.equals(source)
                || SOURCE_PUBLIC_CONTENT_QUERY.equals(source);
    }

    private static boolean isDisplayableItem(DiscoveryMapDTO.DiscoveryItemDTO item) {
        return item != null
                && isDisplayableSource(item.getSource())
                && hasSafeHref(item.getHref())
                && hasSafeText(item.getTitle())
                && hasSafeText(item.getSummary())
                && hasSafeText(item.getReason());
    }

    private static boolean hasSafeHref(String href) {
        String normalized = href == null ? "" : href.toLowerCase();
        return StringUtils.hasText(href)
                && href.startsWith("/")
                && !href.startsWith("//")
                && !normalized.contains("fallback")
                && !normalized.contains("demo")
                && !normalized.contains("fixture")
                && !normalized.contains("local_demo");
    }

    private static boolean hasSafeText(String value) {
        if (!StringUtils.hasText(value)) {
            return true;
        }
        String normalized = value.toLowerCase();
        return !normalized.contains("fallback-demo")
                && !normalized.contains("demo seed")
                && !normalized.contains("fixture")
                && !normalized.contains("local_demo")
                && FORBIDDEN_DISCOVERY_TEXT.stream()
                .noneMatch(forbidden -> normalized.contains(forbidden.toLowerCase()));
    }
}
