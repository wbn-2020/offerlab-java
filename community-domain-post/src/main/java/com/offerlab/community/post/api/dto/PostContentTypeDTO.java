package com.offerlab.community.post.api.dto;

public record PostContentTypeDTO(
        int value,
        String code,
        String label,
        String shortLabel,
        String description,
        String placeholder,
        int minContentLength,
        boolean legacy
) {
}
