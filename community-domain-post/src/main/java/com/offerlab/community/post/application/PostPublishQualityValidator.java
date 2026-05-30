package com.offerlab.community.post.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
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
    private static final int MAX_CONTENT_LEN = 20000;
    private static final int MAX_EXT_JSON_LEN = 20000;
    private static final int MAX_TAG_COUNT = 20;
    private static final int MAX_TAG_NAME_LEN = 32;
    private static final int MAX_META_TEXT_LEN = 64;

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
        int minContentLen = type == Post.TYPE_INTERVIEW ? MIN_INTERVIEW_CONTENT_LEN : MIN_GENERAL_CONTENT_LEN;
        requireLength(normalizedContent, minContentLen, MAX_CONTENT_LEN,
                type == Post.TYPE_INTERVIEW
                        ? "面经正文至少需要 120 个字符，请补充面试过程、问题和复盘"
                        : "正文至少需要 40 个字符，请补充可阅读内容");

        validateTagCount(type, normalizedTagIds, normalizedTagNames);
        String normalizedExtJson = normalizeExtJson(type, extJson);
        return new ValidatedPostInput(type, normalizedTitle, normalizedContent, normalizedExtJson,
                normalizedTagIds, normalizedTagNames);
    }

    private int normalizePostType(Integer postType) {
        if (postType == null) {
            fail("postType", "请选择内容类型");
        }
        if (postType == Post.TYPE_INTERVIEW || postType == Post.TYPE_BLOG
                || postType == Post.TYPE_SOLUTION || postType == Post.TYPE_QA) {
            return postType;
        }
        fail("postType", "内容类型无效");
        return Post.TYPE_INTERVIEW;
    }

    private String normalizeExtJson(int postType, String extJson) {
        String raw = clean(extJson);
        if (raw.isBlank()) {
            if (postType == Post.TYPE_INTERVIEW) {
                throw fieldErrors(Map.of(
                        "company", "面经公司不能为空，且至少 2 个字符",
                        "position", "面经岗位不能为空，且至少 2 个字符"
                ), "面经需要填写公司和岗位信息");
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
            if (postType == Post.TYPE_INTERVIEW) {
                normalizeRequiredText(object, "company", 2, MAX_META_TEXT_LEN,
                        "面经公司不能为空，且至少 2 个字符");
                normalizeRequiredText(object, "position", 2, MAX_META_TEXT_LEN,
                        "面经岗位不能为空，且至少 2 个字符");
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
        int min = postType == Post.TYPE_INTERVIEW ? 2 : 1;
        if (unique.size() < min) {
            fail("tags", postType == Post.TYPE_INTERVIEW
                    ? "面经至少需要 2 个技术标签，方便后续自动提题和检索"
                    : "至少需要 1 个标签，方便内容检索");
        }
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
            case "round", "interviewRound" -> "面试轮次";
            case "yearsOfExp" -> "工作年限";
            case "interviewResult" -> "面试结果";
            case "interviewRounds" -> "面试轮数";
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
