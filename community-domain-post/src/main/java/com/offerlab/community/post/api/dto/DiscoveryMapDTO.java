package com.offerlab.community.post.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscoveryMapDTO {
    private String source;
    private Boolean degraded;
    private String fallbackReason;
    private LocalDateTime generatedAt;
    private Map<String, DiscoveryModuleDTO> modules;
    private List<DiscoveryItemDTO> featuredTopics;
    private List<DiscoveryItemDTO> channels;
    private List<DiscoveryItemDTO> contentForms;
    private List<DiscoveryItemDTO> activeTopics;
    private List<DiscoveryItemDTO> searchEntrypoints;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiscoveryModuleDTO {
        private String key;
        private String title;
        private String status;
        private String source;
        private Boolean degraded;
        private String fallbackReason;
        private Integer itemCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiscoveryItemDTO {
        private String id;
        private String type;
        private String title;
        private String summary;
        private String href;
        private String source;
        private String slug;
        private Long sourceId;
        private Integer domain;
        private Integer postType;
        private String icon;
        private List<String> tags;
        private String reason;
        private String reasonText;
        private String assetStatus;
        private String visibilityState;
        private String previewSource;
        private String excludedReason;
        private LocalDateTime updatedAt;
    }
}
