package com.offerlab.community.question.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.api.dto.TagDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class InterviewMaterialGenerator {
    private static final Pattern METRIC_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?\\s*(?:%|ms|s|秒|分钟|小时|QPS|TPS|w|万|k|K|MB|GB|倍|次))");
    private static final List<String> TECH_KEYWORDS = List.of(
            "Java", "Spring", "Spring Boot", "Spring Cloud", "MySQL", "Redis", "Kafka", "MQ",
            "Elasticsearch", "ES", "Docker", "Kubernetes", "JVM", "GC", "MyBatis", "Gateway",
            "Nginx", "Sentinel", "Seata", "XXL-JOB");

    private final ObjectMapper objectMapper;

    public GeneratedMaterial generate(PostDTO post) {
        String title = clean(post == null ? null : post.getTitle());
        String content = clean(post == null ? null : post.getContent());
        JsonNode ext = parseExt(post == null ? null : post.getExtJson());
        String company = firstText(ext, "company", "");
        String position = firstText(ext, "position", firstText(ext, "scenario", ""));
        String summary = firstText(ext, "summary", "");
        List<String> techStacks = techStacks(post, ext, content);
        List<String> metrics = metrics(content);
        List<String> missing = missingHints(company, position, techStacks, metrics, content);

        String situation = buildSituation(title, summary, company, position, content);
        String task = buildTask(title, position, content);
        String action = buildAction(techStacks, content);
        String result = buildResult(metrics, content);
        List<String> bullets = resumeBullets(title, techStacks, metrics, action, result);
        List<String> followUps = followUps(techStacks, company, position);
        List<String> highlights = highlights(techStacks, content);

        return new GeneratedMaterial(situation, task, action, result, bullets, followUps, highlights, missing);
    }

    private String buildSituation(String title, String summary, String company, String position, String content) {
        if (!summary.isBlank()) {
            return limit(summary, 500);
        }
        String prefix = "围绕《" + fallback(title, "这段项目经验") + "》";
        if (!company.isBlank() || !position.isBlank()) {
            prefix += "，在" + fallback(company, "目标公司") + " / " + fallback(position, "目标岗位或场景") + "背景下";
        }
        return limit(prefix + "沉淀真实工程问题、业务约束和技术上下文。"
                + fallback(firstMeaningfulLine(content), ""), 500);
    }

    private String buildTask(String title, String position, String content) {
        String line = firstLineContaining(content, List.of("目标", "需求", "问题", "挑战", "瓶颈", "故障", "优化"));
        if (!line.isBlank()) {
            return limit(line, 500);
        }
        return limit("目标是把" + fallback(title, "该项目经验") + "讲清楚：说明问题背景、关键难点、方案取舍、落地过程和可量化结果。"
                + (position.isBlank() ? "" : "重点贴合" + position + "场景。"), 500);
    }

    private String buildAction(List<String> techStacks, String content) {
        List<String> actionLines = meaningfulLines(content).stream()
                .filter(line -> containsAny(line, List.of("方案", "实现", "设计", "引入", "优化", "改造", "排查", "定位", "治理", "缓存", "异步", "降级")))
                .limit(3)
                .toList();
        if (!actionLines.isEmpty()) {
            return limit(String.join("；", actionLines), 900);
        }
        String stackText = techStacks.isEmpty() ? "核心技术栈" : String.join("、", techStacks);
        return limit("围绕" + stackText + "拆解方案：先定位瓶颈和约束，再设计落地路径，最后通过监控、压测或回归验证效果。", 900);
    }

    private String buildResult(List<String> metrics, String content) {
        String line = firstLineContaining(content, List.of("结果", "收益", "提升", "降低", "稳定", "复盘", "效果"));
        if (!line.isBlank()) {
            return limit(line, 500);
        }
        if (!metrics.isEmpty()) {
            return limit("结果可围绕这些指标展开：" + String.join("、", metrics) + "。建议补充指标口径和上线后的业务影响。", 500);
        }
        return "结果部分需要补充量化数据，例如耗时、吞吐、错误率、成本、稳定性或用户影响。";
    }

    private List<String> resumeBullets(String title, List<String> techStacks, List<String> metrics, String action, String result) {
        String stackText = techStacks.isEmpty() ? "Java 后端工程能力" : String.join("、", techStacks.subList(0, Math.min(4, techStacks.size())));
        String metricText = metrics.isEmpty() ? "提升系统稳定性与问题定位效率" : "核心指标：" + String.join("、", metrics.subList(0, Math.min(3, metrics.size())));
        return List.of(
                limit("负责《" + fallback(title, "核心项目") + "》关键模块建设，基于" + stackText + "完成问题定位、方案设计和落地验证。", 260),
                limit("围绕业务约束拆解技术取舍：" + compact(action, 160), 260),
                limit("沉淀复盘结果与可追问素材，" + metricText + "；面试中可展开方案边界、风险和后续优化。", 260)
        );
    }

    private List<String> followUps(List<String> techStacks, String company, String position) {
        List<String> result = new ArrayList<>();
        String context = (!company.isBlank() || !position.isBlank())
                ? "在" + fallback(company, "该公司") + " / " + fallback(position, "该岗位") + "场景下"
                : "在这个项目里";
        result.add(context + "，你为什么选择这个方案，而不是更简单的替代方案？");
        result.add("方案上线前后如何验证效果？有没有压测、监控或灰度数据？");
        result.add("这个设计的最大风险是什么？如果流量扩大 10 倍会先改哪里？");
        for (String stack : techStacks) {
            result.add("如果面试官追问 " + stack + "，你能讲清楚哪些原理、配置和边界条件？");
            if (result.size() >= 6) {
                break;
            }
        }
        result.add("这段经历如何压缩成 1 分钟、3 分钟和 5 分钟三个版本？");
        return unique(result, 6);
    }

    private List<String> highlights(List<String> techStacks, String content) {
        List<String> result = new ArrayList<>();
        for (String stack : techStacks) {
            result.add(stack + " 在真实业务场景中的落地、取舍和风险控制");
        }
        meaningfulLines(content).stream()
                .filter(line -> containsAny(line, List.of("一致性", "高并发", "性能", "缓存", "消息", "索引", "事务", "监控", "降级", "限流")))
                .map(line -> limit(line, 160))
                .forEach(result::add);
        if (result.isEmpty()) {
            result.add("可突出问题拆解、方案权衡、上线验证和复盘改进闭环");
        }
        return unique(result, 8);
    }

    private List<String> missingHints(String company, String position, List<String> techStacks, List<String> metrics, String content) {
        List<String> result = new ArrayList<>();
        if (company.isBlank()) result.add("缺少公司或业务背景，建议补充项目所处组织、用户或业务线。");
        if (position.isBlank()) result.add("缺少岗位/场景信息，建议说明这段经历适合投递或面试的方向。");
        if (techStacks.isEmpty()) result.add("缺少明确技术栈，建议补充 Java、Redis、Kafka、MySQL 等关键标签。");
        if (metrics.isEmpty()) result.add("缺少量化结果，建议补充 QPS、耗时、错误率、成本或稳定性指标。");
        if (!containsAny(content, List.of("取舍", "为什么", "权衡", "替代", "方案"))) {
            result.add("缺少技术取舍，建议说明为什么这样设计以及放弃了哪些替代方案。");
        }
        if (!containsAny(content, List.of("复盘", "防复发", "监控", "告警", "灰度"))) {
            result.add("缺少复盘闭环，建议补充监控、防复发或后续优化动作。");
        }
        return result;
    }

    private List<String> techStacks(PostDTO post, JsonNode ext, String content) {
        Set<String> stacks = new LinkedHashSet<>();
        JsonNode rawStacks = ext.path("techStacks");
        if (rawStacks.isArray()) {
            rawStacks.forEach(item -> addClean(stacks, item.asText()));
        } else if (rawStacks.isTextual()) {
            for (String item : rawStacks.asText("").split("[,，、]")) {
                addClean(stacks, item);
            }
        }
        if (post != null && post.getTags() != null) {
            post.getTags().stream().map(TagDTO::getName).forEach(name -> addClean(stacks, name));
        }
        String lower = content.toLowerCase(Locale.ROOT);
        for (String keyword : TECH_KEYWORDS) {
            if (lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                stacks.add(keyword);
            }
        }
        return stacks.stream().filter(item -> item.length() <= 32).limit(10).toList();
    }

    private List<String> metrics(String content) {
        Matcher matcher = METRIC_PATTERN.matcher(content);
        List<String> result = new ArrayList<>();
        while (matcher.find()) {
            result.add(matcher.group(1).replaceAll("\\s+", ""));
            if (result.size() >= 8) {
                break;
            }
        }
        return unique(result, 8);
    }

    private JsonNode parseExt(String extJson) {
        if (extJson == null || extJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(extJson);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private String firstText(JsonNode node, String field, String fallback) {
        String value = clean(node.path(field).asText(""));
        return value.isBlank() ? fallback : value;
    }

    private List<String> meaningfulLines(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        return content.lines()
                .map(line -> line.replaceAll("[#>*`\\-]+", " ").replaceAll("\\s+", " ").trim())
                .filter(line -> line.length() >= 8)
                .limit(80)
                .toList();
    }

    private String firstMeaningfulLine(String content) {
        List<String> lines = meaningfulLines(content);
        return lines.isEmpty() ? "" : lines.get(0);
    }

    private String firstLineContaining(String content, List<String> keywords) {
        return meaningfulLines(content).stream()
                .filter(line -> containsAny(line, keywords))
                .findFirst()
                .orElse("");
    }

    private boolean containsAny(String value, List<String> keywords) {
        String text = clean(value).toLowerCase(Locale.ROOT);
        return keywords.stream().anyMatch(item -> text.contains(item.toLowerCase(Locale.ROOT)));
    }

    private List<String> unique(List<String> values, int limit) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String value : values) {
            String cleaned = clean(value);
            if (!cleaned.isBlank()) {
                set.add(cleaned);
            }
        }
        return set.stream().limit(limit).toList();
    }

    private void addClean(Set<String> set, String value) {
        String cleaned = clean(value);
        if (!cleaned.isBlank()) {
            set.add(cleaned);
        }
    }

    private String fallback(String value, String fallback) {
        String cleaned = clean(value);
        return cleaned.isBlank() ? fallback : cleaned;
    }

    private String compact(String value, int max) {
        return limit(clean(value).replaceAll("\\s+", " "), max);
    }

    private String limit(String value, int max) {
        String cleaned = clean(value);
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public record GeneratedMaterial(String starSituation,
                                    String starTask,
                                    String starAction,
                                    String starResult,
                                    List<String> resumeBullets,
                                    List<String> followUpQuestions,
                                    List<String> technicalHighlights,
                                    List<String> missingHints) {
    }
}
