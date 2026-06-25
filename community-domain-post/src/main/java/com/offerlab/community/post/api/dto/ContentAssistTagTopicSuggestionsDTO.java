package com.offerlab.community.post.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentAssistTagTopicSuggestionsDTO {
    private String provider;
    private Boolean fallbackUsed;
    private Integer domain;
    private String domainName;
    private List<TagSuggestionDTO> tags;
    private List<TopicSuggestionDTO> topics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TagSuggestionDTO {
        private Long id;
        private String name;
        private String reason;
        private Boolean official;
        private Boolean recommended;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopicSuggestionDTO {
        private Long id;
        private String slug;
        private String name;
        private String reason;
        private Boolean virtualTopic;
    }
}
