package com.offerlab.community.analytics.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreatorGrowthWorkspaceDTO {

    private String source;
    private Integer periodDays;
    private boolean degraded;
    private String fallbackReason;
    private CreatorWorkspaceSummaryDTO summary;
    private TrustedContentDTO trustedContent;
    private List<MaintainablePostDTO> maintainablePosts;
    private List<CreatorCurationFeedbackDTO> curationFeedback;
    private List<WorkspaceActionDTO> actions;

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
    public static class CreatorWorkspaceSummaryDTO {
        private Long publicPostCount;
        private Long recentFavoriteCount;
        private Long recentCommentCount;
        private Integer curationInclusionCount;
        private Integer representativePostCount;
        private Integer replyOpportunityCount;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TrustedContentDTO {
        private boolean degraded;
        private String fallbackReason;
        private Long pendingSuggestions;
        private Long freshnessAwaitingConfirmation;
        private Long unresolvedQuestions;
        private Long usefulFeedback7Days;
        private Long usefulFeedback30Days;
        private Long effectiveReads7Days;
        private Long effectiveReads30Days;
        private List<TrustedContentTaskItemDTO> pendingSuggestionItems;
        private List<TrustedContentTaskItemDTO> freshnessItems;
        private List<TrustedContentTaskItemDTO> pendingQuestionItems;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TrustedContentTaskItemDTO {
        private Long postId;
        private Long suggestionId;
        private String postTitle;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private String href;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MaintainablePostDTO {
        private Long postId;
        private String title;
        private String type;
        private String visibility;
        private String href;
        private String editHref;
        private String primarySignal;
        private String signalText;
        private String suggestedAction;
        private String reasonText;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class WorkspaceActionDTO {
        private String actionId;
        private String type;
        private String label;
        private String href;
        private Long postId;
        private String ideaId;
        private String source;
        private String reasonText;
    }

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
