package com.offerlab.community.search.application;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class SearchQuerySpec {

    private static final Pattern TOKEN_SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "的", "了", "和", "与", "及", "在", "是", "我", "有", "请", "帮", "一下",
            "the", "a", "an", "and", "or", "of", "to", "in", "for"
    );

    private final String normalized;
    private final List<String> terms;

    private SearchQuerySpec(String normalized, List<String> terms) {
        this.normalized = normalized;
        this.terms = List.copyOf(terms);
    }

    static SearchQuerySpec from(String raw) {
        String normalized = normalize(raw);
        if (normalized.isBlank()) {
            return new SearchQuerySpec("", List.of());
        }
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String token : TOKEN_SEPARATOR.split(normalized)) {
            String value = token.trim();
            if (!isMeaningful(value)) {
                continue;
            }
            terms.add(value);
        }
        return new SearchQuerySpec(normalized, new ArrayList<>(terms));
    }

    static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return Normalizer.normalize(raw, Normalizer.Form.NFKC)
                .trim()
                .replaceAll("\\s+", " ");
    }

    String normalized() {
        return normalized;
    }

    List<String> terms() {
        return terms;
    }

    boolean emptyInput() {
        return normalized.isBlank();
    }

    boolean hasMeaningfulTerms() {
        return !terms.isEmpty();
    }

    boolean requiresNoMatches() {
        return !emptyInput() && !hasMeaningfulTerms() && !numericIdentifier();
    }

    boolean numericIdentifier() {
        return normalized.chars().allMatch(Character::isDigit);
    }

    int minimumTermMatches() {
        int count = terms.size();
        if (count <= 2) {
            return count;
        }
        if (count <= 4) {
            return count - 1;
        }
        return Math.max(3, (int) Math.ceil(count * 0.75D));
    }

    private static boolean isMeaningful(String value) {
        if (value.isBlank()) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (STOP_WORDS.contains(lower)) {
            return false;
        }
        return value.codePointCount(0, value.length()) > 1 && !value.chars().allMatch(Character::isDigit);
    }
}
