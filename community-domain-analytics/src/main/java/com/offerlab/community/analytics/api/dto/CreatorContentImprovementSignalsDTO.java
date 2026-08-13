package com.offerlab.community.analytics.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreatorContentImprovementSignalsDTO {
    private Integer periodDays;
    private boolean degraded;
    private String fallbackReason;
    private List<Item> items;
    private String nextCursor;
    private boolean hasMore;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Item {
        private Long postId;
        private String postTitle;
        private Integer domain;
        private String domainName;
        private String state;
        private String headline;
        private String detail;
        private String postHref;
        private String editHref;
        private String workspaceHref;
    }
}
