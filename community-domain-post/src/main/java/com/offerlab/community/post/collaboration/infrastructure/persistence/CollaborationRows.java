package com.offerlab.community.post.collaboration.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

public final class CollaborationRows {

    private CollaborationRows() {
    }

    @Data
    public static class NeedRow {
        private Long id;
        private Long followId;
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
        private LocalDateTime claimedAt;
        private LocalDateTime lastProgressAt;
        private Long mergedIntoNeedId;
        private String resolutionType;
        private Long resolutionId;
        private Long resolutionPostId;
        private String closedReason;
        private Long submittedByUid;
        private LocalDateTime submittedAt;
        private String submissionResolutionType;
        private Long submissionResolutionId;
        private String submissionNote;
        private String rejectReason;
        private Integer followerCount;
        private Integer followed;
        private Integer hidden;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class NeedEventRow {
        private Long id;
        private Long needId;
        private String eventType;
        private Long actorUid;
        private String fromStatus;
        private String toStatus;
        private String targetType;
        private Long targetId;
        private String note;
        private String visibilityScope;
        private LocalDateTime createTime;
    }

    @Data
    public static class NeedClaimCycleRow {
        private Long id;
        private Long needId;
        private Integer cycleNo;
        private Long claimantUid;
        private String status;
        private String cycleOrigin;
        private LocalDateTime claimedAt;
        private LocalDateTime lastProgressAt;
        private LocalDateTime endedAt;
        private String endReason;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class NeedRevisionRow {
        private Long id;
        private Long needId;
        private Long cycleId;
        private Integer cycleNo;
        private Integer revisionNo;
        private Long submitterUid;
        private String resolutionType;
        private Long resolutionId;
        private Long resolutionPostId;
        private String note;
        private String status;
        private String revisionOrigin;
        private LocalDateTime submittedAt;
        private Long decidedBy;
        private LocalDateTime decidedAt;
        private String decisionNote;
        private String visibilityScope;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class NeedFollowRow {
        private Long id;
        private Long uid;
    }

    @Data
    public static class SeriesRow {
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
        private Integer hidden;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class SeriesMemberRow {
        private Long id;
        private Long seriesId;
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
    public static class SubmissionRow {
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
    public static class OfficeHourRow {
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
        private String status;
        private Integer hidden;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class OfficeHourReservationRow {
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
        private Integer hidden;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class OfficeHourFeedbackRow {
        private Long id;
        private Long reservationId;
        private Long officeHourId;
        private Long authorUid;
        private Long targetUid;
        private Integer rating;
        private String feedback;
        private Integer hidden;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class ActivityRow {
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
        private Integer hidden;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class CurationRow {
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
    public static class DiscussionRow {
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
        private Integer hidden;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class DiscussionOptionRow {
        private Long id;
        private Long discussionId;
        private String text;
        private Integer sortOrder;
        private Integer voteCount;
    }

    @Data
    public static class DiscussionVoteRow {
        private Long discussionId;
        private Long optionId;
    }

    @Data
    public static class PostRefRow {
        private Long id;
        private Long authorUid;
        private Integer postType;
        private Integer domain;
        private Integer visibility;
        private Integer status;
        private Integer deleted;
    }

    @Data
    public static class TopicRefRow {
        private Long id;
        private String topicName;
        private Integer domain;
        private String allowedDomains;
        private Integer status;
        private Integer deleted;
    }

    @Data
    public static class GovernanceCaseRow {
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
