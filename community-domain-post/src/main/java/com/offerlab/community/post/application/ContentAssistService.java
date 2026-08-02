package com.offerlab.community.post.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.post.api.dto.CommunityTopicDTO;
import com.offerlab.community.post.api.dto.ContentAssistQualityScoreCmd;
import com.offerlab.community.post.api.dto.ContentAssistQualityScoreDTO;
import com.offerlab.community.post.api.dto.ContentAssistTagTopicSuggestionsCmd;
import com.offerlab.community.post.api.dto.ContentAssistTagTopicSuggestionsDTO;
import com.offerlab.community.post.api.dto.ContentAssistWritingCmd;
import com.offerlab.community.post.api.dto.ContentAssistWritingDTO;
import com.offerlab.community.post.api.dto.TagDTO;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.domain.model.PostDomain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentAssistService {

    private static final int MAX_LIST_ITEMS = 6;
    private static final int MAX_ASSIST_CONTEXT_CHARS = 1200;
    private static final int MAX_ASSIST_CONTEXT_VALUE_CHARS = 160;
    private static final String PRIVATE_CAREER_BOUNDARY_ERROR = "PRIVATE_CAREER_TRAINING_BOUNDARY";
    private static final List<String> ASSIST_CONTEXT_FIELDS = List.of(
            "source", "action", "contextType", "title", "postType", "topic",
            "reasonText", "contextSource", "keyword", "clusterId", "topicSlug",
            "templateCode", "degraded", "degradedReason");

    private final ObjectMapper objectMapper;
    private final ContentAssistAiClient aiClient;
    private final ContentAssistTaxonomyService taxonomyService;
    private final ContentAssistRecordGateway recordGateway;

    public ContentAssistWritingDTO assistWriting(Long uid, ContentAssistWritingCmd cmd) {
        Integer domain = normalizeOptionalDomain(cmd == null ? null : cmd.getDomain());
        validatePostType(cmd == null ? null : cmd.getPostType());
        String title = limit(clean(cmd == null ? null : cmd.getTitle()), 255);
        String content = requireContent(cmd == null ? null : cmd.getContent());
        List<String> tagNames = normalizeTagNames(cmd == null ? null : cmd.getTagNames());
        String assistContext = normalizeAssistContext(cmd == null ? null : cmd.getAssistContext());
        String assistTemplateCode = limit(clean(cmd == null ? null : cmd.getAssistTemplateCode()), 64);
        if (isPrivateCareerTrainingContent(title, content + "\n" + assistContext,
                appendAssistTemplate(tagNames, assistTemplateCode))) {
            ContentAssistWritingDTO result = boundaryWriting();
            record(uid, ContentAssistScene.WRITING, "rules", "RULE_BOUNDARY", domain, content,
                    0, 0, 0L, PRIVATE_CAREER_BOUNDARY_ERROR);
            return result;
        }
        ContentAssistWritingDTO rule = ruleWriting(domain, cmd == null ? null : cmd.getPostType(), title, content, tagNames);
        record(uid, ContentAssistScene.WRITING, "rules", "RULE_ONLY", domain, content, 0, 0, 0L, null);
        return rule;
    }

    public ContentAssistQualityScoreDTO scoreQuality(Long uid, ContentAssistQualityScoreCmd cmd) {
        Integer domain = normalizeOptionalDomain(cmd == null ? null : cmd.getDomain());
        validatePostType(cmd == null ? null : cmd.getPostType());
        String title = limit(clean(cmd == null ? null : cmd.getTitle()), 255);
        String content = requireContent(cmd == null ? null : cmd.getContent());
        List<String> tagNames = normalizeTagNames(cmd == null ? null : cmd.getTagNames());
        String assistContext = normalizeAssistContext(cmd == null ? null : cmd.getAssistContext());
        String assistTemplateCode = limit(clean(cmd == null ? null : cmd.getAssistTemplateCode()), 64);
        if (isPrivateCareerTrainingContent(title, content + "\n" + assistContext,
                appendAssistTemplate(tagNames, assistTemplateCode))) {
            ContentAssistQualityScoreDTO result = boundaryQuality();
            record(uid, ContentAssistScene.QUALITY_SCORE, "rules", "RULE_BOUNDARY", domain, content,
                    0, 0, 0L, PRIVATE_CAREER_BOUNDARY_ERROR);
            return result;
        }
        ContentAssistQualityScoreDTO rule = ruleQuality(domain, cmd == null ? null : cmd.getPostType(), title, content, tagNames);
        record(uid, ContentAssistScene.QUALITY_SCORE, "rules", "RULE_ONLY", domain, content, 0, 0, 0L, null);
        return rule;
    }

    public ContentAssistTagTopicSuggestionsDTO suggestTagsAndTopics(Long uid, ContentAssistTagTopicSuggestionsCmd cmd) {
        Integer domain = normalizeOptionalDomain(cmd == null ? null : cmd.getDomain());
        String title = limit(clean(cmd == null ? null : cmd.getTitle()), 255);
        String content = requireContent(cmd == null ? null : cmd.getContent());
        String assistContext = normalizeAssistContext(cmd == null ? null : cmd.getAssistContext());
        String assistTemplateCode = limit(clean(cmd == null ? null : cmd.getAssistTemplateCode()), 64);
        if (isPrivateCareerTrainingContent(title, content + "\n" + assistContext,
                appendAssistTemplate(List.of(), assistTemplateCode))) {
            ContentAssistTagTopicSuggestionsDTO result = boundaryTagTopicSuggestions(domain);
            record(uid, ContentAssistScene.TAG_TOPIC_SUGGESTIONS, "rules", "RULE_BOUNDARY", domain, content,
                    0, 0, 0L, PRIVATE_CAREER_BOUNDARY_ERROR);
            return result;
        }
        ContentAssistTagTopicSuggestionsDTO result = ruleTagTopicSuggestions(
                domain, title, content, assistContext, assistTemplateCode);
        record(uid, ContentAssistScene.TAG_TOPIC_SUGGESTIONS, "rules", "RULE_ONLY", domain, content, 0, 0, 0L, null);
        return result;
    }

    private AssistExecution<ContentAssistWritingDTO> tryAiWriting(Integer domain,
                                                                  Integer postType,
                                                                  String title,
                                                                  String content,
                                                                  List<String> tagNames,
                                                                  String assistContext,
                                                                  String assistTemplateCode,
                                                                  ContentAssistWritingDTO rule) {
        if (!aiClient.enabled() || !aiClient.configured()) {
            return AssistExecution.rule(rule);
        }
        ContentAssistPrompt prompt = new ContentAssistPrompt(
                domain, postType, title, content, tagNames, assistContext, assistTemplateCode);
        try {
            ContentAssistAiClient.Completion completion = aiClient.complete(ContentAssistScene.WRITING, prompt);
            try {
                ContentAssistWritingDTO ai = mergeWriting(rule, parseWritingPayload(completion.contentJson()));
                return AssistExecution.success(ai, completion.provider(), completion.promptTokens(),
                        completion.completionTokens(), completion.estimatedCostMicros());
            } catch (InvalidAiResponseException e) {
                log.warn("content assist ai invalid: scene={} hash={} error={}",
                        ContentAssistScene.WRITING, shortHash(content), e.getMessage());
                return AssistExecution.fallback(
                        withWritingMeta(rule, true, completion.promptTokens(), completion.completionTokens(),
                                completion.estimatedCostMicros(), "AI_INVALID_RESPONSE"),
                        completion.provider(), "AI_INVALID_RESPONSE", completion.promptTokens(),
                        completion.completionTokens(), completion.estimatedCostMicros());
            }
        } catch (Exception e) {
            String errorCode = normalizeErrorCode(e);
            log.warn("content assist ai failed: scene={} hash={} error={}",
                    ContentAssistScene.WRITING, shortHash(content), errorCode);
            return AssistExecution.fallback(withWritingMeta(rule, true, 0, 0, 0L, errorCode),
                    "deepseek", errorCode, 0, 0, 0L);
        }
    }

    private AssistExecution<ContentAssistQualityScoreDTO> tryAiQuality(Integer domain,
                                                                       Integer postType,
                                                                       String title,
                                                                       String content,
                                                                       List<String> tagNames,
                                                                       String assistContext,
                                                                       String assistTemplateCode,
                                                                       ContentAssistQualityScoreDTO rule) {
        if (!aiClient.enabled() || !aiClient.configured()) {
            return AssistExecution.rule(rule);
        }
        ContentAssistPrompt prompt = new ContentAssistPrompt(
                domain, postType, title, content, tagNames, assistContext, assistTemplateCode);
        try {
            ContentAssistAiClient.Completion completion = aiClient.complete(ContentAssistScene.QUALITY_SCORE, prompt);
            try {
                ContentAssistQualityScoreDTO ai = mergeQuality(rule, parseQualityPayload(completion.contentJson()));
                return AssistExecution.success(ai, completion.provider(), completion.promptTokens(),
                        completion.completionTokens(), completion.estimatedCostMicros());
            } catch (InvalidAiResponseException e) {
                log.warn("content assist ai invalid: scene={} hash={} error={}",
                        ContentAssistScene.QUALITY_SCORE, shortHash(content), e.getMessage());
                return AssistExecution.fallback(
                        withQualityMeta(rule, true, completion.promptTokens(), completion.completionTokens(),
                                completion.estimatedCostMicros(), "AI_INVALID_RESPONSE"),
                        completion.provider(), "AI_INVALID_RESPONSE", completion.promptTokens(),
                        completion.completionTokens(), completion.estimatedCostMicros());
            }
        } catch (Exception e) {
            String errorCode = normalizeErrorCode(e);
            log.warn("content assist ai failed: scene={} hash={} error={}",
                    ContentAssistScene.QUALITY_SCORE, shortHash(content), errorCode);
            return AssistExecution.fallback(withQualityMeta(rule, true, 0, 0, 0L, errorCode),
                    "deepseek", errorCode, 0, 0, 0L);
        }
    }

    private ContentAssistWritingDTO ruleWriting(Integer domain, Integer postType, String title, String content, List<String> tagNames) {
        List<String> outline = outlineTemplate(postType);
        List<String> suggestions = new ArrayList<>();
        if (title.length() < 8) {
            suggestions.add("标题可以补充对象、动作和结果，让读者更快判断价值。");
        }
        if (content.length() < 120) {
            suggestions.add("正文建议补充背景、步骤、结果，方便读者复用经验。");
        }
        if (!containsAny(content, List.of("结果", "效果", "指标", "复盘", "结论"))) {
            suggestions.add("补充结果或指标，帮助内容形成闭环。");
        }
        if (tagNames.isEmpty()) {
            suggestions.add("发布前补充 1-3 个已有标签，方便话题归档。");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("结构已经比较完整，发布前再检查事实和敏感信息。");
        }
        List<String> riskHints = List.of(
                "AI 建议不替代内容审核",
                "发布前请人工确认事实、隐私与敏感信息");
        return ContentAssistWritingDTO.builder()
                .provider("rules")
                .fallbackUsed(false)
                .promptTokens(0)
                .completionTokens(0)
                .estimatedCostMicros(0L)
                .suggestedTitle(suggestedTitle(title, content, postType))
                .summary(summary(content, 90))
                .outline(outline)
                .suggestions(limitList(suggestions, MAX_LIST_ITEMS, 80))
                .riskHints(limitList(riskHints, 4, 80))
                .build();
    }

    private ContentAssistWritingDTO boundaryWriting() {
        return ContentAssistWritingDTO.builder()
                .provider("rules")
                .fallbackUsed(true)
                .promptTokens(0)
                .completionTokens(0)
                .estimatedCostMicros(0L)
                .errorCode(PRIVATE_CAREER_BOUNDARY_ERROR)
                .suggestedTitle(null)
                .summary("编辑器助手只服务公共内容生产，涉及私人求职准备记录时不生成写作、标签、话题或系列建议。")
                .outline(List.of())
                .suggestions(List.of())
                .riskHints(List.of("请移除私人材料后，再继续编辑公开经验内容。", "公共内容生产建议不处理个人求职准备记录。"))
                .build();
    }

    private ContentAssistQualityScoreDTO ruleQuality(Integer domain, Integer postType, String title, String content, List<String> tagNames) {
        int titleScore = scoreTitle(title);
        int contentScore = scoreContent(content);
        int structureScore = scoreStructure(content, postType);
        int tagsScore = scoreTags(tagNames);
        int domainScore = PostDomain.isValid(domain) ? 10 : 4;
        int total = Math.max(0, Math.min(100, titleScore + contentScore + structureScore + tagsScore + domainScore));
        List<ContentAssistQualityScoreDTO.DimensionDTO> explanations = List.of(
                ContentAssistQualityScoreDTO.DimensionDTO.builder()
                        .dimension("title")
                        .score(titleScore)
                        .reason(titleReason(title))
                        .build(),
                ContentAssistQualityScoreDTO.DimensionDTO.builder()
                        .dimension("content")
                        .score(contentScore)
                        .reason(contentReason(content))
                        .build(),
                ContentAssistQualityScoreDTO.DimensionDTO.builder()
                        .dimension("structure")
                        .score(structureScore)
                        .reason(structureReason(content, postType))
                        .build(),
                ContentAssistQualityScoreDTO.DimensionDTO.builder()
                        .dimension("tags")
                        .score(tagsScore)
                        .reason(tagsReason(tagNames))
                        .build(),
                ContentAssistQualityScoreDTO.DimensionDTO.builder()
                        .dimension("domain")
                        .score(domainScore)
                        .reason(PostDomain.isValid(domain) ? "已绑定现有领域，便于后续检索与归档。" : "未绑定明确领域，建议发布前确认内容归属。")
                        .build());
        List<String> suggestions = new ArrayList<>();
        if (contentScore < 28) {
            suggestions.add("补充更完整的背景、步骤与结果，提高可复用性。");
        }
        if (structureScore < 14) {
            suggestions.add("建议拆成背景、问题、方案、结果四段，增强可读性。");
        }
        if (tagsScore < 8) {
            suggestions.add("增加 1-3 个已有标签，方便社区检索。");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("整体质量较稳，发布前再做一次事实校对。");
        }
        return ContentAssistQualityScoreDTO.builder()
                .score(total)
                .level(level(total))
                .advisoryOnly(true)
                .summary("该分数仅用于发布前建议，不替代审核或自动通过。")
                .suggestions(limitList(suggestions, MAX_LIST_ITEMS, 80))
                .explanations(explanations)
                .provider("rules")
                .fallbackUsed(false)
                .promptTokens(0)
                .completionTokens(0)
                .estimatedCostMicros(0L)
                .build();
    }

    private ContentAssistQualityScoreDTO boundaryQuality() {
        return ContentAssistQualityScoreDTO.builder()
                .score(0)
                .level("NEEDS_WORK")
                .advisoryOnly(true)
                .summary("命中公共内容生产边界，当前仅返回边界提示，不进入质量评分。")
                .suggestions(List.of())
                .explanations(List.of(ContentAssistQualityScoreDTO.DimensionDTO.builder()
                        .dimension("safety")
                        .score(0)
                        .reason("涉及私人求职准备记录时，编辑器助手不会生成质量建议。")
                        .build()))
                .provider("rules")
                .fallbackUsed(true)
                .promptTokens(0)
                .completionTokens(0)
                .estimatedCostMicros(0L)
                .errorCode(PRIVATE_CAREER_BOUNDARY_ERROR)
                .build();
    }

    private ContentAssistTagTopicSuggestionsDTO ruleTagTopicSuggestions(Integer domain,
                                                                         String title,
                                                                         String content,
                                                                         String assistContext,
                                                                         String assistTemplateCode) {
        List<TagDTO> tags = taxonomyService.listTags();
        List<CommunityTopicDTO> topics = taxonomyService.listTopics();
        String text = (title + "\n" + content + "\n" + assistContext + "\n" + assistTemplateCode)
                .toLowerCase(Locale.ROOT);
        List<MatchedTag> matchedTags = tags.stream()
                .filter(this::isActiveTag)
                .map(tag -> matchTag(tag, text))
                .filter(match -> match.score() > 0)
                .sorted(Comparator
                        .comparingInt(MatchedTag::score).reversed()
                        .thenComparing(match -> Boolean.TRUE.equals(match.tag().getRecommended()), Comparator.reverseOrder())
                        .thenComparing(match -> Boolean.TRUE.equals(match.tag().getOfficial()), Comparator.reverseOrder()))
                .limit(5)
                .toList();
        if (matchedTags.isEmpty()) {
            matchedTags = tags.stream()
                    .filter(this::isActiveTag)
                    .sorted(Comparator
                            .comparing((TagDTO tag) -> Boolean.TRUE.equals(tag.getRecommended())).reversed()
                            .thenComparing(tag -> Boolean.TRUE.equals(tag.getOfficial()), Comparator.reverseOrder()))
                    .limit(3)
                    .map(tag -> new MatchedTag(tag, 1, "结合当前领域补全已有标签"))
                    .toList();
        }
        Set<Long> matchedTagIds = matchedTags.stream()
                .map(match -> match.tag().getId())
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> matchedTagNames = matchedTags.stream()
                .map(match -> clean(match.tag().getName()).toLowerCase(Locale.ROOT))
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        List<ContentAssistTagTopicSuggestionsDTO.TopicSuggestionDTO> topicDtos = topics.stream()
                .filter(this::isActiveTopic)
                .map(topic -> matchTopic(topic, text, matchedTagIds, matchedTagNames))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(TopicMatch::score).reversed())
                .limit(3)
                .map(TopicMatch::dto)
                .toList();
        return ContentAssistTagTopicSuggestionsDTO.builder()
                .provider("rules")
                .fallbackUsed(false)
                .domain(domain)
                .domainName(PostDomain.isValid(domain) ? PostDomain.fromCode(domain).getDisplayName() : null)
                .tags(matchedTags.stream().map(match -> ContentAssistTagTopicSuggestionsDTO.TagSuggestionDTO.builder()
                        .id(match.tag().getId())
                        .name(match.tag().getName())
                        .reason(match.reason())
                        .official(Boolean.TRUE.equals(match.tag().getOfficial()))
                        .recommended(Boolean.TRUE.equals(match.tag().getRecommended()))
                        .build()).toList())
                .topics(topicDtos)
                .build();
    }

    private ContentAssistTagTopicSuggestionsDTO boundaryTagTopicSuggestions(Integer domain) {
        return ContentAssistTagTopicSuggestionsDTO.builder()
                .provider("rules")
                .fallbackUsed(true)
                .domain(domain)
                .domainName(PostDomain.isValid(domain) ? PostDomain.fromCode(domain).getDisplayName() : null)
                .tags(List.of())
                .topics(List.of())
                .build();
    }

    private WritingPayload parseWritingPayload(String json) {
        JsonNode root = readJsonObject(json);
        String suggestedTitle = nullableText(root.get("suggestedTitle"), 80);
        String summary = nullableText(root.get("summary"), 120);
        List<String> outline = stringList(root.get("outline"), MAX_LIST_ITEMS, 40);
        List<String> suggestions = stringList(root.get("suggestions"), MAX_LIST_ITEMS, 80);
        List<String> riskHints = stringList(root.get("riskHints"), 4, 80);
        int validCount = (StringUtils.hasText(suggestedTitle) ? 1 : 0)
                + (StringUtils.hasText(summary) ? 1 : 0)
                + (!outline.isEmpty() ? 1 : 0)
                + (!suggestions.isEmpty() ? 1 : 0)
                + (!riskHints.isEmpty() ? 1 : 0);
        if (validCount == 0) {
            throw new InvalidAiResponseException("writing payload is empty");
        }
        return new WritingPayload(suggestedTitle, summary, outline, suggestions, riskHints);
    }

    private QualityPayload parseQualityPayload(String json) {
        JsonNode root = readJsonObject(json);
        Integer score = nullableInt(root.get("score"));
        String summary = nullableText(root.get("summary"), 120);
        List<String> suggestions = stringList(root.get("suggestions"), MAX_LIST_ITEMS, 80);
        List<ContentAssistQualityScoreDTO.DimensionDTO> explanations = explanationList(root.get("explanations"));
        if (score == null && !StringUtils.hasText(summary) && suggestions.isEmpty() && explanations.isEmpty()) {
            throw new InvalidAiResponseException("quality payload is empty");
        }
        return new QualityPayload(score, summary, suggestions, explanations);
    }

    private ContentAssistWritingDTO mergeWriting(ContentAssistWritingDTO rule, WritingPayload payload) {
        return withWritingMeta(ContentAssistWritingDTO.builder()
                .suggestedTitle(defaultText(payload.suggestedTitle(), rule.getSuggestedTitle()))
                .summary(defaultText(payload.summary(), rule.getSummary()))
                .outline(payload.outline().isEmpty() ? rule.getOutline() : payload.outline())
                .suggestions(payload.suggestions().isEmpty() ? rule.getSuggestions() : payload.suggestions())
                .riskHints(payload.riskHints().isEmpty() ? rule.getRiskHints() : payload.riskHints())
                .build(), false, 0, 0, 0L, null);
    }

    private ContentAssistQualityScoreDTO mergeQuality(ContentAssistQualityScoreDTO rule, QualityPayload payload) {
        int score = payload.score() == null ? rule.getScore() : Math.max(0, Math.min(payload.score(), 100));
        return withQualityMeta(ContentAssistQualityScoreDTO.builder()
                .score(score)
                .level(level(score))
                .advisoryOnly(true)
                .summary(defaultText(payload.summary(), rule.getSummary()))
                .suggestions(payload.suggestions().isEmpty() ? rule.getSuggestions() : payload.suggestions())
                .explanations(payload.explanations().isEmpty() ? rule.getExplanations() : payload.explanations())
                .build(), false, 0, 0, 0L, null);
    }

    private ContentAssistWritingDTO withWritingMeta(ContentAssistWritingDTO dto,
                                                    boolean fallbackUsed,
                                                    int promptTokens,
                                                    int completionTokens,
                                                    long estimatedCostMicros,
                                                    String errorCode) {
        return ContentAssistWritingDTO.builder()
                .provider(defaultText(dto.getProvider(), "rules"))
                .fallbackUsed(fallbackUsed)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .estimatedCostMicros(estimatedCostMicros)
                .errorCode(errorCode)
                .suggestedTitle(dto.getSuggestedTitle())
                .summary(dto.getSummary())
                .outline(dto.getOutline())
                .suggestions(dto.getSuggestions())
                .riskHints(dto.getRiskHints())
                .build();
    }

    private ContentAssistQualityScoreDTO withQualityMeta(ContentAssistQualityScoreDTO dto,
                                                         boolean fallbackUsed,
                                                         int promptTokens,
                                                         int completionTokens,
                                                         long estimatedCostMicros,
                                                         String errorCode) {
        return ContentAssistQualityScoreDTO.builder()
                .score(dto.getScore())
                .level(dto.getLevel())
                .advisoryOnly(Boolean.TRUE.equals(dto.getAdvisoryOnly()))
                .summary(dto.getSummary())
                .suggestions(dto.getSuggestions())
                .explanations(dto.getExplanations())
                .provider(defaultText(dto.getProvider(), "rules"))
                .fallbackUsed(fallbackUsed)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .estimatedCostMicros(estimatedCostMicros)
                .errorCode(errorCode)
                .build();
    }

    private void record(Long uid,
                        ContentAssistScene scene,
                        String provider,
                        String status,
                        Integer domain,
                        String content,
                        int promptTokens,
                        int completionTokens,
                        long estimatedCostMicros,
                        String errorCode) {
        try {
            recordGateway.save(new ContentAssistAuditRecord(
                    uid,
                    scene.name(),
                    provider,
                    status,
                    domain,
                    content == null ? 0 : content.length(),
                    ContentAssistSafety.sha256Hex(content),
                    promptTokens,
                    completionTokens,
                    Math.max(0L, estimatedCostMicros),
                    limit(errorCode, 64)));
        } catch (Exception e) {
            log.warn("content assist record degraded: scene={} hash={} error={}",
                    scene.name(), shortHash(content), e.getMessage());
        }
    }

    private MatchedTag matchTag(TagDTO tag, String text) {
        int score = 0;
        String reason = null;
        String name = clean(tag.getName()).toLowerCase(Locale.ROOT);
        if (StringUtils.hasText(name) && text.contains(name)) {
            score += 3;
            reason = "正文命中了现有标签关键词";
        }
        for (String synonym : tag.getSynonyms() == null ? List.<String>of() : tag.getSynonyms()) {
            String candidate = clean(synonym).toLowerCase(Locale.ROOT);
            if (StringUtils.hasText(candidate) && text.contains(candidate)) {
                score += 2;
                reason = "正文命中了标签别名";
                break;
            }
        }
        if (Boolean.TRUE.equals(tag.getRecommended())) {
            score++;
        }
        if (Boolean.TRUE.equals(tag.getOfficial())) {
            score++;
        }
        return new MatchedTag(tag, score, reason == null ? "结合当前领域补全已有标签" : reason);
    }

    private TopicMatch matchTopic(CommunityTopicDTO topic,
                                  String text,
                                  Set<Long> matchedTagIds,
                                  Set<String> matchedTagNames) {
        int score = 0;
        String reason = null;
        if (topic == null) {
            return null;
        }
        String name = clean(topic.getName()).toLowerCase(Locale.ROOT);
        String slug = clean(topic.getSlug()).toLowerCase(Locale.ROOT);
        if (StringUtils.hasText(name) && text.contains(name)) {
            score += 3;
            reason = "正文直接命中了已有专题名称";
        }
        if (StringUtils.hasText(slug) && text.contains(slug.replace("-", " "))) {
            score += 2;
            reason = reason == null ? "正文命中了已有专题 slug" : reason;
        }
        for (TagDTO tag : topic.getTags() == null ? List.<TagDTO>of() : topic.getTags()) {
            if (tag == null) {
                continue;
            }
            if (!isActiveTag(tag)) {
                continue;
            }
            if (tag.getId() != null && matchedTagIds.contains(tag.getId())) {
                score += 3;
                reason = "匹配到已存在专题标签";
            } else if (StringUtils.hasText(tag.getName()) && matchedTagNames.contains(clean(tag.getName()).toLowerCase(Locale.ROOT))) {
                score += 2;
                reason = "匹配到已存在专题标签";
            }
        }
        if (score <= 0) {
            return null;
        }
        return new TopicMatch(score, ContentAssistTagTopicSuggestionsDTO.TopicSuggestionDTO.builder()
                .id(topic.getId())
                .slug(topic.getSlug())
                .name(topic.getName())
                .reason(reason == null ? "与现有专题内容相关" : reason)
                .virtualTopic(Boolean.TRUE.equals(topic.getVirtualTopic()))
                .build());
    }

    private boolean isActiveTag(TagDTO tag) {
        return tag != null
                && !Objects.equals(tag.getStatus(), 0)
                && StringUtils.hasText(tag.getName())
                && isAllowedSuggestionSource(tag.getName(), tag.getSlug(), tag.getCategory(),
                tag.getSynonyms() == null ? null : String.join(" ", tag.getSynonyms()));
    }

    private boolean isActiveTopic(CommunityTopicDTO topic) {
        return topic != null
                && !Objects.equals(topic.getStatus(), 0)
                && StringUtils.hasText(topic.getName())
                && isAllowedSuggestionSource(topic.getName(), topic.getSlug(), topic.getDescription(), topic.getTopicType());
    }

    private boolean isPrivateCareerTrainingContent(String title, String content, List<String> tagNames) {
        String source = (clean(title) + "\n" + clean(content) + "\n" + String.join("\n", tagNames)).toLowerCase(Locale.ROOT);
        int sensitive = 0;
        if (source.contains("简历")) {
            sensitive++;
        }
        if (source.contains("jd")) {
            sensitive++;
        }
        if (source.contains("投递")) {
            sensitive++;
        }
        if (source.contains("模拟面试")) {
            sensitive++;
        }
        boolean privateCue = source.contains("私人") || source.contains("个人") || source.contains("训练");
        return sensitive >= 1 && (privateCue || sensitive >= 2);
    }

    private boolean isAllowedSuggestionSource(String... values) {
        String source = String.join("\n", values == null ? new String[0] : values).toLowerCase(Locale.ROOT);
        return !source.contains("简历")
                && !source.contains("jd")
                && !source.contains("投递")
                && !source.contains("模拟面试")
                && !source.contains("私人训练");
    }

    private JsonNode readJsonObject(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isObject()) {
                throw new InvalidAiResponseException("ai payload must be json object");
            }
            return node;
        } catch (InvalidAiResponseException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidAiResponseException("ai payload parse failed");
        }
    }

    private List<ContentAssistQualityScoreDTO.DimensionDTO> explanationList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<ContentAssistQualityScoreDTO.DimensionDTO> result = new ArrayList<>();
        for (JsonNode item : node) {
            if (item == null || !item.isObject()) {
                continue;
            }
            String dimension = nullableText(item.get("dimension"), 32);
            Integer score = nullableInt(item.get("score"));
            String reason = nullableText(item.get("reason"), 120);
            if (!StringUtils.hasText(dimension) || score == null || !StringUtils.hasText(reason)) {
                continue;
            }
            result.add(ContentAssistQualityScoreDTO.DimensionDTO.builder()
                    .dimension(dimension)
                    .score(Math.max(0, Math.min(score, 100)))
                    .reason(reason)
                    .build());
            if (result.size() >= MAX_LIST_ITEMS) {
                break;
            }
        }
        return result;
    }

    private List<String> stringList(JsonNode node, int maxItems, int maxLen) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : node) {
            String value = nullableText(item, maxLen);
            if (!StringUtils.hasText(value)) {
                continue;
            }
            result.add(value);
            if (result.size() >= maxItems) {
                break;
            }
        }
        return result;
    }

    private Integer nullableInt(JsonNode node) {
        return node != null && node.canConvertToInt() ? node.asInt() : null;
    }

    private String nullableText(JsonNode node, int maxLen) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        return limit(node.asText(), maxLen);
    }

    private List<String> outlineTemplate(Integer postType) {
        return switch (postType == null ? 0 : postType) {
            case Post.TYPE_PROJECT_REVIEW -> List.of("背景", "难点", "方案", "结果");
            case Post.TYPE_PITFALL -> List.of("现象", "根因", "修复方案", "指标结果");
            case Post.TYPE_SYSTEM_DESIGN -> List.of("目标与约束", "核心模块", "数据流", "取舍");
            case Post.TYPE_INTERVIEW_RECAP -> List.of("公开背景", "问题与追问", "表达卡点", "复盘结论");
            default -> List.of("背景", "问题", "方案", "结果复盘");
        };
    }

    private Integer normalizeOptionalDomain(Integer domain) {
        if (domain == null) {
            return null;
        }
        if (!PostDomain.isValid(domain)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return domain;
    }

    private void validatePostType(Integer postType) {
        if (postType != null && !Post.isSupportedType(postType)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private String requireContent(String content) {
        String safe = clean(content);
        if (!StringUtils.hasText(safe)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return safe;
    }

    private List<String> normalizeTagNames(List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) {
            return List.of();
        }
        Set<String> dedup = new LinkedHashSet<>();
        for (String tagName : tagNames) {
            String value = limit(clean(tagName), 64);
            if (!StringUtils.hasText(value)) {
                continue;
            }
            dedup.add(value);
            if (dedup.size() >= 8) {
                break;
            }
        }
        return List.copyOf(dedup);
    }

    private List<String> appendAssistTemplate(List<String> tagNames, String assistTemplateCode) {
        if (!StringUtils.hasText(assistTemplateCode)) {
            return tagNames;
        }
        List<String> values = new ArrayList<>(tagNames == null ? List.of() : tagNames);
        values.add(assistTemplateCode);
        return values;
    }

    private String normalizeAssistContext(JsonNode assistContext) {
        if (assistContext == null || !assistContext.isObject()) {
            return "";
        }
        List<String> values = new ArrayList<>();
        int totalLength = 0;
        for (String field : ASSIST_CONTEXT_FIELDS) {
            JsonNode node = assistContext.get(field);
            if (node == null || node.isContainerNode() || node.isNull()) {
                continue;
            }
            String value = limit(clean(node.asText()), MAX_ASSIST_CONTEXT_VALUE_CHARS);
            if (!StringUtils.hasText(value)) {
                continue;
            }
            String entry = field + "=" + value;
            if (totalLength + entry.length() + 1 > MAX_ASSIST_CONTEXT_CHARS) {
                break;
            }
            values.add(entry);
            totalLength += entry.length() + 1;
        }
        return String.join("\n", values);
    }

    private int scoreTitle(String title) {
        int length = clean(title).length();
        if (length >= 12 && length <= 36) {
            return 20;
        }
        if (length >= 8) {
            return 15;
        }
        if (length >= 4) {
            return 10;
        }
        return 4;
    }

    private int scoreContent(String content) {
        int length = clean(content).length();
        if (length >= 260) {
            return 40;
        }
        if (length >= 180) {
            return 34;
        }
        if (length >= 120) {
            return 28;
        }
        if (length >= 80) {
            return 20;
        }
        return 10;
    }

    private int scoreStructure(String content, Integer postType) {
        int matched = 0;
        for (String keyword : outlineTemplate(postType)) {
            if (clean(content).contains(keyword)) {
                matched++;
            }
        }
        return Math.min(20, matched * 5);
    }

    private int scoreTags(List<String> tagNames) {
        int size = tagNames == null ? 0 : tagNames.size();
        if (size >= 2) {
            return 10;
        }
        if (size == 1) {
            return 7;
        }
        return 3;
    }

    private String titleReason(String title) {
        int length = clean(title).length();
        if (length < 8) {
            return "标题偏短，建议补充对象、动作和结果。";
        }
        if (length > 36) {
            return "标题信息较多，发布前可以再压缩重点。";
        }
        return "标题长度适中，便于读者快速判断价值。";
    }

    private String contentReason(String content) {
        int length = clean(content).length();
        if (length < 120) {
            return "正文偏短，建议补充更多上下文与结果。";
        }
        if (length < 200) {
            return "正文已有主要信息，可以继续补充复盘与指标。";
        }
        return "正文信息量较完整，适合沉淀为社区经验。";
    }

    private String structureReason(String content, Integer postType) {
        int matched = 0;
        for (String keyword : outlineTemplate(postType)) {
            if (clean(content).contains(keyword)) {
                matched++;
            }
        }
        if (matched <= 1) {
            return "结构信号较弱，建议按背景、问题、方案、结果组织。";
        }
        if (matched <= 3) {
            return "已经有明显结构，继续强化结果和取舍说明会更好。";
        }
        return "结构较清晰，读者能较快跟上内容节奏。";
    }

    private String tagsReason(List<String> tagNames) {
        int size = tagNames == null ? 0 : tagNames.size();
        if (size == 0) {
            return "暂未补充标签，可能影响后续检索与话题归档。";
        }
        if (size == 1) {
            return "已有基础标签，建议再补充 1 个主题标签。";
        }
        return "标签数量合适，便于检索与归档。";
    }

    private String level(int score) {
        if (score >= 85) {
            return "EXCELLENT";
        }
        if (score >= 70) {
            return "GOOD";
        }
        if (score >= 55) {
            return "FAIR";
        }
        return "NEEDS_WORK";
    }

    private String suggestedTitle(String title, String content, Integer postType) {
        if (StringUtils.hasText(title) && title.length() >= 8) {
            return title;
        }
        String summary = summary(content, 26);
        if (postType == null) {
            return summary;
        }
        return switch (postType) {
            case Post.TYPE_PROJECT_REVIEW -> summary + "项目复盘";
            case Post.TYPE_PITFALL -> summary + "踩坑复盘";
            case Post.TYPE_SYSTEM_DESIGN -> summary + "设计拆解";
            default -> summary;
        };
    }

    private String summary(String content, int maxLen) {
        String normalized = clean(content).replaceAll("\\s+", " ");
        return normalized.length() <= maxLen ? normalized : normalized.substring(0, maxLen) + "...";
    }

    private boolean containsAny(String text, Collection<String> keywords) {
        String safe = clean(text);
        return keywords.stream().anyMatch(safe::contains);
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private List<String> limitList(List<String> values, int maxItems, int maxLen) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String text = limit(clean(value), maxLen);
            if (!StringUtils.hasText(text)) {
                continue;
            }
            result.add(text);
            if (result.size() >= maxItems) {
                break;
            }
        }
        return result;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String limit(String value, int maxLen) {
        String safe = clean(value);
        return safe.length() <= maxLen ? safe : safe.substring(0, maxLen);
    }

    private String shortHash(String content) {
        return ContentAssistSafety.sha256Hex(content).substring(0, 12);
    }

    private String normalizeErrorCode(Exception e) {
        String message = e == null ? "" : clean(e.getMessage());
        if (message.startsWith("Deepseek HTTP ")) {
            return limit("AI_HTTP_" + message.substring("Deepseek HTTP ".length()), 64);
        }
        if (message.toLowerCase(Locale.ROOT).contains("timeout")) {
            return "AI_TIMEOUT";
        }
        if (message.toLowerCase(Locale.ROOT).contains("not allowed")) {
            return "AI_CONFIG_BLOCKED";
        }
        return "AI_PROVIDER_FAILED";
    }

    private record AssistExecution<T>(T result,
                                      String recordProvider,
                                      String status,
                                      int promptTokens,
                                      int completionTokens,
                                      long estimatedCostMicros,
                                      String errorCode) {
        private static <T> AssistExecution<T> rule(T result) {
            return new AssistExecution<>(result, "rules", "RULE_ONLY", 0, 0, 0L, null);
        }

        private static AssistExecution<ContentAssistWritingDTO> success(ContentAssistWritingDTO dto,
                                                                        String provider,
                                                                        int promptTokens,
                                                                        int completionTokens,
                                                                        long estimatedCostMicros) {
            return new AssistExecution<>(ContentAssistWritingDTO.builder()
                    .provider(provider)
                    .fallbackUsed(false)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .estimatedCostMicros(estimatedCostMicros)
                    .errorCode(null)
                    .suggestedTitle(dto.getSuggestedTitle())
                    .summary(dto.getSummary())
                    .outline(dto.getOutline())
                    .suggestions(dto.getSuggestions())
                    .riskHints(dto.getRiskHints())
                    .build(), provider, "AI_SUCCESS", promptTokens, completionTokens, estimatedCostMicros, null);
        }

        private static AssistExecution<ContentAssistQualityScoreDTO> success(ContentAssistQualityScoreDTO dto,
                                                                             String provider,
                                                                             int promptTokens,
                                                                             int completionTokens,
                                                                             long estimatedCostMicros) {
            return new AssistExecution<>(ContentAssistQualityScoreDTO.builder()
                    .score(dto.getScore())
                    .level(dto.getLevel())
                    .advisoryOnly(true)
                    .summary(dto.getSummary())
                    .suggestions(dto.getSuggestions())
                    .explanations(dto.getExplanations())
                    .provider(provider)
                    .fallbackUsed(false)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .estimatedCostMicros(estimatedCostMicros)
                    .errorCode(null)
                    .build(), provider, "AI_SUCCESS", promptTokens, completionTokens, estimatedCostMicros, null);
        }

        private static <T> AssistExecution<T> fallback(T result,
                                                       String provider,
                                                       String errorCode,
                                                       int promptTokens,
                                                       int completionTokens,
                                                       long estimatedCostMicros) {
            return new AssistExecution<>(result, provider, "AI_FALLBACK", promptTokens, completionTokens,
                    estimatedCostMicros, errorCode);
        }
    }

    private record MatchedTag(TagDTO tag, int score, String reason) {
    }

    private record TopicMatch(int score, ContentAssistTagTopicSuggestionsDTO.TopicSuggestionDTO dto) {
    }

    private record WritingPayload(String suggestedTitle,
                                  String summary,
                                  List<String> outline,
                                  List<String> suggestions,
                                  List<String> riskHints) {
    }

    private record QualityPayload(Integer score,
                                  String summary,
                                  List<String> suggestions,
                                  List<ContentAssistQualityScoreDTO.DimensionDTO> explanations) {
    }

    private static final class InvalidAiResponseException extends RuntimeException {
        private InvalidAiResponseException(String message) {
            super(message);
        }
    }
}
