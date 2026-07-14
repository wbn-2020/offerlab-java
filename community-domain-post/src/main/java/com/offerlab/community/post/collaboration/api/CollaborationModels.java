package com.offerlab.community.post.collaboration.api;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public final class CollaborationModels {

    private CollaborationModels() {
    }

    @Data
    public static class NeedCreateCmd {
        @NotNull
        @Min(1)
        @Max(5)
        private Integer domain;
        @NotBlank
        @Size(max = 32)
        private String sourceType;
        @Positive
        private Long sourceRefId;
        @NotBlank
        @Size(max = 32)
        private String contentFormat;
        @NotBlank
        @Size(max = 120)
        private String title;
        @NotBlank
        @Size(max = 2000)
        private String description;
        @Size(max = 1000)
        private String acceptanceCriteria;
        private Boolean riskAcknowledged;
    }

    @Data
    public static class NeedMergeCmd {
        @NotNull
        @Positive
        private Long targetNeedId;
        @Size(max = 500)
        private String note;
    }

    @Data
    public static class NeedCompleteCmd {
        @Positive
        private Long resolutionPostId;
        @Size(max = 24)
        private String resolutionType;
        @Positive
        private Long resolutionId;
        @Size(max = 500)
        private String note;
    }

    @Data
    public static class NeedClaimCmd {
        private Boolean riskAcknowledged;
    }

    @Data
    public static class CloseCmd {
        @Size(max = 500)
        private String note;
    }

    @Data
    @Builder
    public static class NeedDTO {
        private Long id;
        private Long creatorUid;
        private Integer domain;
        private String sourceType;
        private Long sourceRefId;
        private String contentFormat;
        private String title;
        private String description;
        private String acceptanceCriteria;
        private String status;
        private Long claimedByUid;
        private Long mergedIntoNeedId;
        private String resolutionType;
        private Long resolutionId;
        private Long resolutionPostId;
        private String closedReason;
        private Integer followerCount;
        private Boolean followed;
        private Boolean canManage;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class SeriesCreateCmd {
        @NotNull
        @Min(1)
        @Max(5)
        private Integer domain;
        @NotBlank
        @Size(max = 120)
        private String title;
        @NotBlank
        @Size(max = 2000)
        private String description;
        @Size(max = 1000)
        private String submissionInstructions;
        private Boolean riskAcknowledged;
    }

    @Data
    public static class SeriesMemberCmd {
        @NotNull
        @Positive
        private Long uid;
        @NotBlank
        @Size(max = 24)
        private String role;
    }

    @Data
    public static class PostSubmissionCmd {
        @NotNull
        @Positive
        private Long postId;
        @Size(max = 1000)
        private String note;
        private Boolean riskAcknowledged;
    }

    @Data
    public static class ReviewCmd {
        @NotBlank
        @Size(max = 24)
        private String decision;
        @Size(max = 1000)
        private String note;
    }

    @Data
    @Builder
    public static class SeriesDTO {
        private Long id;
        private Long ownerUid;
        private Integer domain;
        private String title;
        private String description;
        private String submissionInstructions;
        private String status;
        private Integer memberCount;
        private Integer postCount;
        private String currentUserRole;
        private Boolean canManage;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    @Builder
    public static class SeriesMemberDTO {
        private Long id;
        private Long uid;
        private String role;
        private String status;
        private Long addedBy;
        private LocalDateTime exitedAt;
        private LocalDateTime revokedAt;
        private Long revokedBy;
        private LocalDateTime createTime;
    }

    @Data
    @Builder
    public static class SubmissionDTO {
        private Long id;
        private Long parentId;
        private Long postId;
        private Long submitterUid;
        private String note;
        private String reviewStatus;
        private Long reviewerUid;
        private String reviewNote;
        private LocalDateTime reviewedAt;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class OfficeHourCreateCmd {
        @NotNull
        @Min(1)
        @Max(5)
        private Integer domain;
        @NotBlank
        @Size(max = 120)
        private String title;
        @NotBlank
        @Size(max = 2000)
        private String description;
        @Size(max = 1000)
        private String topicGuidance;
        @NotNull
        @Future
        private LocalDateTime startsAt;
        @NotNull
        @Future
        private LocalDateTime endsAt;
        @NotNull
        @Min(1)
        @Max(100)
        private Integer capacity;
        private Boolean riskAcknowledged;
    }

    @Data
    public static class OfficeHourStatusCmd {
        @NotBlank
        @Size(max = 24)
        private String status;
        @Size(max = 500)
        private String note;
    }

    @Data
    @Builder
    public static class OfficeHourDTO {
        private Long id;
        private Long hostUid;
        private Integer domain;
        private String title;
        private String description;
        private String topicGuidance;
        private LocalDateTime startsAt;
        private LocalDateTime endsAt;
        private Integer capacity;
        private Integer reservedCount;
        private Integer availableCount;
        private String status;
        private Boolean canManage;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class OfficeHourReservationCreateCmd {
        @NotBlank
        @Size(max = 160)
        private String topic;
        @Size(max = 1500)
        private String contextDetail;
        private Boolean riskAcknowledged;
    }

    @Data
    public static class OfficeHourReservationDecisionCmd {
        @NotBlank
        @Size(max = 24)
        private String decision;
        @Size(max = 1000)
        private String note;
    }

    @Data
    public static class OfficeHourFeedbackCreateCmd {
        @NotNull
        @Min(1)
        @Max(5)
        private Integer rating;
        @Size(max = 1000)
        private String feedback;
    }

    @Data
    @Builder
    public static class OfficeHourReservationDTO {
        private Long id;
        private Long officeHourId;
        private Long hostUid;
        private Long attendeeUid;
        private String topic;
        private String contextDetail;
        private String status;
        private String responseNote;
        private Long decidedBy;
        private LocalDateTime decidedAt;
        private LocalDateTime hostConfirmedAt;
        private LocalDateTime attendeeConfirmedAt;
        private LocalDateTime completedAt;
        private Long cancelledBy;
        private LocalDateTime cancelledAt;
        private Boolean canManage;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    @Builder
    public static class OfficeHourFeedbackDTO {
        private Long id;
        private Long reservationId;
        private Long officeHourId;
        private Long authorUid;
        private Long targetUid;
        private Integer rating;
        private String feedback;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class ActivityCreateCmd {
        @NotNull
        @Min(1)
        @Max(5)
        private Integer domain;
        @NotBlank
        @Size(max = 32)
        private String activityType;
        @NotBlank
        @Size(max = 120)
        private String title;
        @NotBlank
        @Size(max = 2000)
        private String description;
        @Size(max = 1000)
        private String submissionRule;
        private LocalDateTime startsAt;
        @Future
        private LocalDateTime endsAt;
        private Boolean riskAcknowledged;
    }

    @Data
    public static class ActivitySummaryCmd {
        @NotBlank
        @Size(max = 4000)
        private String resultSummary;
    }

    @Data
    public static class ActivityStatusCmd {
        @NotBlank
        @Size(max = 24)
        private String status;
        @Size(max = 500)
        private String note;
    }

    @Data
    @Builder
    public static class ActivityDTO {
        private Long id;
        private Long ownerUid;
        private Integer domain;
        private String activityType;
        private String title;
        private String description;
        private String submissionRule;
        private String status;
        private String resultSummary;
        private LocalDateTime startsAt;
        private LocalDateTime endsAt;
        private Integer submissionCount;
        private Boolean canManage;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class CurationSuggestionCreateCmd {
        @NotNull
        @Positive
        private Long topicId;
        @NotNull
        @Positive
        private Long postId;
        @NotBlank
        @Size(max = 24)
        private String suggestionType;
        @NotBlank
        @Size(max = 1000)
        private String rationale;
        private Boolean riskAcknowledged;
    }

    @Data
    @Builder
    public static class CurationSuggestionDTO {
        private Long id;
        private Long topicId;
        private String topicName;
        private Long postId;
        private Long submitterUid;
        private Integer domain;
        private String suggestionType;
        private String rationale;
        private String reviewStatus;
        private Long reviewerUid;
        private String reviewNote;
        private LocalDateTime reviewedAt;
        private String resultType;
        private Long resultId;
        private String resultStatus;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class DiscussionCreateCmd {
        @NotNull
        @Positive
        private Long sourcePostId;
        @NotBlank
        @Size(max = 120)
        private String title;
        @NotBlank
        @Size(max = 2000)
        private String prompt;
        @NotNull
        @Size(min = 2, max = 10)
        private List<@NotBlank @Size(max = 300) String> options;
        private Boolean riskAcknowledged;
    }

    @Data
    public static class VoteCmd {
        @NotNull
        @Positive
        private Long optionId;
        private Boolean riskAcknowledged;
    }

    @Data
    public static class DiscussionSummaryCmd {
        @NotBlank
        @Size(max = 4000)
        private String summary;
        @NotBlank
        @Size(max = 24)
        private String consensusState;
        @Size(max = 2000)
        private String authorFollowUp;
        private Boolean closeAfterSummary;
    }

    @Data
    @Builder
    public static class DiscussionOptionDTO {
        private Long id;
        private String text;
        private Integer sortOrder;
        private Integer voteCount;
        private Boolean selected;
    }

    @Data
    @Builder
    public static class DiscussionDTO {
        private Long id;
        private Long creatorUid;
        private Long sourcePostId;
        private Integer domain;
        private String title;
        private String prompt;
        private String status;
        private String summary;
        private String consensusState;
        private String authorFollowUp;
        private Long followedUpBy;
        private LocalDateTime followedUpAt;
        private Long summarizedBy;
        private LocalDateTime summarizedAt;
        private Integer voteCount;
        private Long selectedOptionId;
        private Boolean canManage;
        private List<DiscussionOptionDTO> options;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class GovernanceCaseCreateCmd {
        @NotBlank
        @Size(max = 24)
        private String caseType;
        @NotBlank
        @Size(max = 32)
        private String targetType;
        @NotNull
        @Positive
        private Long targetId;
        @Positive
        private Long parentCaseId;
        @NotBlank
        @Size(max = 32)
        private String reasonCode;
        @NotBlank
        @Size(max = 2000)
        private String detail;
    }

    @Data
    @Builder
    public static class GovernanceCaseDTO {
        private Long id;
        private String caseType;
        private String targetType;
        private Long targetId;
        private Long submitterUid;
        private Long parentCaseId;
        private String reasonCode;
        private String detail;
        private String status;
        private Long reviewerUid;
        private String reviewNote;
        private LocalDateTime reviewedAt;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }
}
