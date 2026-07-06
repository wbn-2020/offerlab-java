package com.offerlab.community.post.api;

import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.TagDTO;

import java.util.Locale;
import java.util.Set;

public final class PublicContentFilter {

    private static final Set<String> UNSAFE_TEXT_MARKERS = Set.of(
            "广告",
            "广告投放",
            "支付解锁",
            "会员",
            "付费曝光",
            "收益",
            "提现",
            "隐私画像",
            "AI 精准",
            "精准推荐",
            "专家建议",
            "权威推荐",
            "专业背书",
            "限时购买",
            "会员专享",
            "官方背书",
            "平台担保",
            "赞助推荐",
            "付费置顶",
            "权威认证",
            "保证有效",
            "商业合作",
            "identity-proof",
            "phone-number",
            "住址",
            "人肉搜索",
            "official guarantee",
            "official endorsement",
            "platform guarantee",
            "authority certified",
            "authority certification",
            "limited-time purchase",
            "member exclusive",
            "sponsored recommendation",
            "paid pin"
    );

    private PublicContentFilter() {
    }

    public static boolean isSyntheticPost(PostBriefDTO post) {
        if (post == null) {
            return false;
        }
        if (isSyntheticText(post.getTitle())
                || isSyntheticText(post.getSummary())
                || isSyntheticText(post.getHighlightTitle())
                || isSyntheticText(post.getHighlightSummary())
                || isSyntheticText(post.getExtJson())) {
            return true;
        }
        if (post.getAuthor() != null
                && (isSyntheticText(post.getAuthor().getNickname()) || isSyntheticText(post.getAuthor().getBio()))) {
            return true;
        }
        if (post.getTags() == null) {
            return false;
        }
        return post.getTags().stream()
                .map(TagDTO::getName)
                .anyMatch(PublicContentFilter::isSyntheticText);
    }

    public static boolean isDistributablePost(PostBriefDTO post) {
        return post != null
                && post.getId() != null
                && !isSyntheticPost(post)
                && !isUnsafeSuggestionText(post.getTitle())
                && !isUnsafeSuggestionText(post.getSummary());
    }

    public static boolean isSyntheticText(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        String compact = upper.replace("-", "").replace("_", "").replace(" ", "");
        return compact.contains("E2E")
                || compact.contains("SMOKE")
                || compact.contains("CODEX")
                || compact.contains("TESTDATA")
                || compact.contains("DEMO")
                || compact.contains("FIXTURE");
    }

    public static boolean isUnsafeSuggestionText(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String text = value.trim().toLowerCase(Locale.ROOT);
        return UNSAFE_TEXT_MARKERS.stream()
                .map(marker -> marker.toLowerCase(Locale.ROOT))
                .anyMatch(text::contains);
    }
}
