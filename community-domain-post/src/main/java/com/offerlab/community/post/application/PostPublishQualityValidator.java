package com.offerlab.community.post.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.post.api.dto.PostContentLimits;
import com.offerlab.community.post.domain.model.Post;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class PostPublishQualityValidator {
    private static final int MIN_TITLE_LEN = 8;
    private static final int MAX_TITLE_LEN = 200;
    private static final int MIN_INTERVIEW_CONTENT_LEN = 120;
    private static final int MIN_GENERAL_CONTENT_LEN = 40;
    private static final int MIN_PROJECT_REVIEW_CONTENT_LEN = 80;
    private static final int MIN_PITFALL_CONTENT_LEN = 60;
    private static final int MIN_LIGHT_COMMUNITY_CONTENT_LEN = 30;
    private static final int MAX_CONTENT_LEN = PostContentLimits.MAX_CONTENT_LEN;
    private static final int MAX_EXT_JSON_LEN = PostContentLimits.MAX_EXT_JSON_LEN;
    private static final int MAX_TAG_COUNT = 20;
    private static final int MAX_TAG_NAME_LEN = 32;
    private static final int MAX_META_TEXT_LEN = 64;
    private static final int MAX_SUMMARY_LEN = 240;
    private static final int MAX_TECH_STACK_COUNT = 12;

    private final ObjectMapper objectMapper;

    public static BizException fieldError(String field, String message) {
        return fieldErrors(Map.of(field, message), message);
    }

    public static BizException fieldErrors(Map<String, String> errors, String message) {
        return new BizException(ErrorCode.PARAM_ERROR.getCode(), message, Map.of("fieldErrors", errors));
    }

    public ValidatedPostInput validate(Integer postType, String title, String content, String extJson,
                                       List<Long> tagIds, List<String> tagNames) {
        int type = normalizePostType(postType);
        String normalizedTitle = clean(title);
        String normalizedContent = clean(content);
        List<Long> normalizedTagIds = normalizeTagIds(tagIds);
        List<String> normalizedTagNames = normalizeTagNames(tagNames);

        requireLength(normalizedTitle, MIN_TITLE_LEN, MAX_TITLE_LEN, "标题需为 8-200 个字符");
        int minContentLen = minContentLenFor(type);
        requireLength(normalizedContent, minContentLen, MAX_CONTENT_LEN,
                Post.isInterviewType(type)
                        ? "历史经验正文至少需要 120 个字符，请补充过程、问题和复盘"
                        : "正文至少需要 " + minContentLen + " 个字符，请补充可阅读内容");
        validateStructuredExperience(type, normalizedContent);

        validateTagCount(type, normalizedTagIds, normalizedTagNames);
        String normalizedExtJson = normalizeExtJson(type, extJson);
        return new ValidatedPostInput(type, normalizedTitle, normalizedContent, normalizedExtJson,
                normalizedTagIds, normalizedTagNames);
    }

    private int normalizePostType(Integer postType) {
        if (postType == null) {
            fail("postType", "请选择内容类型");
        }
        if (Post.isSupportedType(postType)) {
            return postType;
        }
        fail("postType", "内容类型无效");
        return Post.TYPE_INTERVIEW;
    }

    private int minContentLenFor(int postType) {
        return switch (postType) {
            case Post.TYPE_INTERVIEW -> MIN_INTERVIEW_CONTENT_LEN;
            case Post.TYPE_PROJECT_REVIEW, Post.TYPE_SYSTEM_DESIGN, Post.TYPE_INTERVIEW_RECAP -> MIN_PROJECT_REVIEW_CONTENT_LEN;
            case Post.TYPE_PITFALL -> MIN_PITFALL_CONTENT_LEN;
            case Post.TYPE_COMMUNITY_QUESTION, Post.TYPE_RESOURCE, Post.TYPE_NOTE -> MIN_LIGHT_COMMUNITY_CONTENT_LEN;
            default -> MIN_GENERAL_CONTENT_LEN;
        };
    }

    private String normalizeExtJson(int postType, String extJson) {
        String raw = clean(extJson);
        if (raw.isBlank()) {
            if (Post.isInterviewType(postType)) {
                throw fieldErrors(Map.of(
                        "company", "历史经验实体不能为空，且至少 2 个字符",
                        "position", "历史经验场景不能为空，且至少 2 个字符"
                ), "历史经验需要填写实体和场景信息");
            }
            return null;
        }
        if (raw.length() > MAX_EXT_JSON_LEN) {
            fail("extension", "扩展信息过长，请精简后再发布");
        }
        try {
            JsonNode parsed = objectMapper.readTree(raw);
            if (!parsed.isObject()) {
                fail("extension", "扩展信息格式错误：必须是 JSON 对象");
            }
            ObjectNode object = (ObjectNode) parsed.deepCopy();
            normalizeOptionalText(object, "round", MAX_META_TEXT_LEN);
            normalizeOptionalText(object, "interviewRound", MAX_META_TEXT_LEN);
            validateOptionalInt(object, "yearsOfExp", 0, 50);
            validateOptionalInt(object, "interviewResult", 0, 3);
            validateOptionalInt(object, "interviewRounds", 0, 20);
            normalizeOptionalText(object, "difficulty", MAX_META_TEXT_LEN);
            normalizeOptionalText(object, "scenario", MAX_META_TEXT_LEN);
            normalizeOptionalText(object, "contentType", MAX_META_TEXT_LEN);
            normalizeOptionalText(object, "summary", MAX_SUMMARY_LEN);
            normalizeOptionalText(object, "faqJson", MAX_EXT_JSON_LEN / 2);
            normalizeOptionalText(object, "knowledgeCardJson", MAX_EXT_JSON_LEN / 2);
            normalizeOptionalBoolean(object, "featured");
            normalizeOptionalTextArray(object, "techStacks", MAX_TECH_STACK_COUNT, MAX_TAG_NAME_LEN);
            if (Post.isInterviewType(postType)) {
                normalizeRequiredText(object, "company", 2, MAX_META_TEXT_LEN,
                        "历史经验实体不能为空，且至少 2 个字符");
                normalizeRequiredText(object, "position", 2, MAX_META_TEXT_LEN,
                        "历史经验场景不能为空，且至少 2 个字符");
            } else {
                normalizeOptionalText(object, "company", MAX_META_TEXT_LEN);
                normalizeOptionalText(object, "position", MAX_META_TEXT_LEN);
            }
            String normalized = objectMapper.writeValueAsString(object);
            if (normalized.length() > MAX_EXT_JSON_LEN) {
                fail("extension", "扩展信息过长，请精简后再发布");
            }
            return normalized;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            fail("extension", "扩展信息格式错误：请提交合法 JSON");
            return null;
        }
    }

    private void normalizeRequiredText(ObjectNode object, String field, int minLen, int maxLen, String message) {
        String value = clean(object.path(field).asText(""));
        if (value.length() < minLen) {
            fail(field, message);
        }
        if (value.length() > maxLen) {
            fail(field, fieldLabel(field) + "不能超过 " + maxLen + " 个字符");
        }
        object.put(field, value);
    }

    private void normalizeOptionalText(ObjectNode object, String field, int maxLen) {
        if (!object.has(field) || object.get(field).isNull()) {
            return;
        }
        String value = clean(object.path(field).asText(""));
        if (value.isBlank()) {
            object.remove(field);
            return;
        }
        if (value.length() > maxLen) {
            fail(field, fieldLabel(field) + "不能超过 " + maxLen + " 个字符");
        }
        object.put(field, value);
    }

    private void validateOptionalInt(ObjectNode object, String field, int min, int max) {
        if (!object.has(field) || object.get(field).isNull()) {
            return;
        }
        JsonNode node = object.get(field);
        String raw = node.isTextual() ? node.asText().trim() : node.asText();
        if (raw.isBlank()) {
            object.remove(field);
            return;
        }
        try {
            int value = Integer.parseInt(raw);
            if (value < min || value > max) {
                fail(field, fieldLabel(field) + "超出允许范围");
            }
            object.put(field, value);
        } catch (NumberFormatException e) {
            fail(field, fieldLabel(field) + "必须是整数");
        }
    }

    private void normalizeOptionalBoolean(ObjectNode object, String field) {
        if (!object.has(field) || object.get(field).isNull()) {
            return;
        }
        JsonNode node = object.get(field);
        if (node.isBoolean()) {
            return;
        }
        String raw = node.asText("").trim();
        if (raw.isBlank()) {
            object.remove(field);
            return;
        }
        if ("true".equalsIgnoreCase(raw) || "1".equals(raw)) {
            object.put(field, true);
            return;
        }
        if ("false".equalsIgnoreCase(raw) || "0".equals(raw)) {
            object.put(field, false);
            return;
        }
        fail(field, fieldLabel(field) + "必须是布尔值");
    }

    private void normalizeOptionalTextArray(ObjectNode object, String field, int maxCount, int maxItemLen) {
        if (!object.has(field) || object.get(field).isNull()) {
            return;
        }
        JsonNode node = object.get(field);
        List<String> items = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = clean(item.asText(""));
                if (!value.isBlank()) {
                    items.add(value);
                }
            }
        } else {
            String raw = clean(node.asText(""));
            if (!raw.isBlank()) {
                items.addAll(List.of(raw.split("[,，、]")));
            }
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String item : items) {
            String value = clean(item).replaceAll("\\s+", " ");
            if (value.isBlank()) {
                continue;
            }
            if (value.length() > maxItemLen) {
                fail(field, fieldLabel(field) + "单项不能超过 " + maxItemLen + " 个字符");
            }
            normalized.add(value);
        }
        if (normalized.size() > maxCount) {
            fail(field, fieldLabel(field) + "最多只能填写 " + maxCount + " 项");
        }
        if (normalized.isEmpty()) {
            object.remove(field);
            return;
        }
        object.set(field, objectMapper.valueToTree(List.copyOf(normalized)));
    }

    private List<Long> normalizeTagIds(List<Long> tagIds) {
        if (tagIds == null) {
            return List.of();
        }
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (Long tagId : tagIds) {
            if (tagId == null || tagId <= 0) {
                fail("tags", "标签 ID 无效");
            }
            result.add(tagId);
        }
        if (result.size() > MAX_TAG_COUNT) {
            fail("tags", "最多只能选择 20 个标签");
        }
        return List.copyOf(result);
    }

    private List<String> normalizeTagNames(List<String> tagNames) {
        if (tagNames == null) {
            return List.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String raw : tagNames) {
            String name = clean(raw).replaceAll("\\s+", " ");
            if (name.isBlank()) {
                continue;
            }
            if (name.length() > MAX_TAG_NAME_LEN) {
                fail("tags", "标签名称不能超过 32 个字符");
            }
            if (containsControlChar(name)) {
                fail("tags", "标签名称包含无效字符");
            }
            result.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
        }
        if (result.size() > MAX_TAG_COUNT) {
            fail("tags", "最多只能选择 20 个标签");
        }
        return List.copyOf(result.values());
    }

    private void validateTagCount(int postType, List<Long> tagIds, List<String> tagNames) {
        Set<String> unique = new LinkedHashSet<>();
        tagIds.forEach(id -> unique.add("id:" + id));
        tagNames.forEach(name -> unique.add("name:" + name.toLowerCase(Locale.ROOT)));
        if (unique.size() > MAX_TAG_COUNT) {
            fail("tags", "最多只能选择 20 个标签");
        }
        int min = Post.isInterviewType(postType) ? 2 : 1;
        if (unique.size() < min) {
            fail("tags", Post.isInterviewType(postType)
                    ? "历史经验至少需要 2 个技术标签，方便后续自动结构化和检索"
                    : "至少需要 1 个标签，方便内容检索");
        }
    }

    private void validateStructuredExperience(int postType, String content) {
        if (postType != Post.TYPE_PROJECT_REVIEW && postType != Post.TYPE_PITFALL) {
            return;
        }
        Map<String, List<String>> requiredGroups = new LinkedHashMap<>();
        requiredGroups.put("背景或现象", List.of("背景", "现象", "上下文"));
        requiredGroups.put("难点或根因", List.of("难点", "根因", "问题", "挑战", "瓶颈"));
        requiredGroups.put("方案与落地", List.of("方案", "修复", "实现", "设计", "落地"));
        requiredGroups.put("结果或指标", List.of("结果", "指标", "收益", "提升", "降低", "复盘"));

        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : requiredGroups.entrySet()) {
            if (!containsAny(content, entry.getValue())) {
                missing.add(entry.getKey());
            }
        }
        if (!missing.isEmpty()) {
            String typeName = postType == Post.TYPE_PROJECT_REVIEW ? "项目复盘" : "故障复盘";
            fail("content", typeName + "需要包含" + String.join("、", missing) + "，便于沉淀为可参考的经验");
        }
    }

    private boolean containsAny(String value, List<String> keywords) {
        String text = clean(value);
        return keywords.stream().anyMatch(text::contains);
    }

    private void requireLength(String value, int min, int max, String message) {
        if (value.length() < min || value.length() > max) {
            fail(message.startsWith("标题") ? "title" : "content", message);
        }
    }

    private boolean containsControlChar(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String fieldLabel(String field) {
        return switch (field) {
            case "company" -> "公司";
            case "position" -> "岗位";
            case "round", "interviewRound" -> "记录轮次";
            case "yearsOfExp" -> "工作年限";
            case "interviewResult" -> "历史结果";
            case "interviewRounds" -> "记录轮数";
            case "difficulty" -> "内容难度";
            case "scenario" -> "场景";
            case "contentType" -> "内容类型";
            case "summary" -> "摘要";
            case "faqJson" -> "问答沉淀";
            case "knowledgeCardJson" -> "知识卡片";
            case "featured" -> "精选标记";
            case "techStacks" -> "技术栈";
            default -> field;
        };
    }

    private void fail(String field, String message) {
        throw fieldError(field, message);
    }

    public record ValidatedPostInput(Integer postType, String title, String content, String extJson,
                                     List<Long> tagIds, List<String> tagNames) {
        public ValidatedPostInput {
            tagIds = tagIds == null ? List.of() : List.copyOf(new ArrayList<>(tagIds));
            tagNames = tagNames == null ? List.of() : List.copyOf(new ArrayList<>(tagNames));
        }
    }
}
