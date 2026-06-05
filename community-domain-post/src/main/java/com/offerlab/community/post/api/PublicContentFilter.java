package com.offerlab.community.post.api;

import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.TagDTO;

import java.util.Locale;

public final class PublicContentFilter {

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

    public static boolean isSyntheticText(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        String compact = upper.replace("-", "").replace("_", "").replace(" ", "");
        return compact.contains("E2E")
                || compact.contains("SMOKE")
                || compact.contains("CODEX")
                || compact.contains("TESTDATA");
    }
}
