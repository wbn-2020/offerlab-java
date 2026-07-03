package com.offerlab.community.analytics.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreatorGrowthWorkspaceDTO {

    private CreatorFeedbackSummaryDTO creatorFeedbackSummary;
    private List<CreatorTopPostDTO> creatorTopPosts;
    private List<CreatorReplyOpportunityDTO> creatorReplyOpportunities;
    private List<RepresentativePostDTO> representativePosts;
    private List<PublicSeriesDTO> publicSeries;
    private List<CreatorTopicIdeaDTO> creatorTopicIdeas;
    private CreatorDigestNotificationDTO creatorDigestNotification;
    private List<String> nonPaymentIncentiveCopy;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CreatorFeedbackSummaryDTO {
        private String headline;
        private List<FeedbackWindowDTO> windows;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FeedbackWindowDTO {
        private String key;
        private int days;
        private Long postCount;
        private Long likeCount;
        private Long favoriteCount;
        private Long commentCount;
        private Long viewCount;
        private Long feedbackCount;
        private String trendText;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CreatorTopPostDTO {
        private Long postId;
        private String title;
        private Integer domain;
        private String domainName;
        private Long feedbackCount;
        private boolean featured;
        private String reason;
        private String visibilityScope;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CreatorReplyOpportunityDTO {
        private Long commentId;
        private Long postId;
        private String postTitle;
        private String commentExcerpt;
        private Long likeCount;
        private String reason;
        private Long commenterUid;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RepresentativePostDTO {
        private Long postId;
        private String title;
        private Integer domain;
        private String domainName;
        private Long feedbackCount;
        private String source;
        private boolean publicVisible;
        private String boundaryCopy;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PublicSeriesDTO {
        private Long seriesId;
        private String title;
        private String description;
        private Integer domain;
        private String domainName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CreatorTopicIdeaDTO {
        private String ideaId;
        private String title;
        private String source;
        private String reason;
        private String suggestedContentType;
        private Map<String, String> jumpParams;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CreatorDigestNotificationDTO {
        private String frequency;
        private String copy;
    }
}
