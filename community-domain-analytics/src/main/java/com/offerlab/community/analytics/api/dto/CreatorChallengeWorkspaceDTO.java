package com.offerlab.community.analytics.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreatorChallengeWorkspaceDTO {
    private List<CreatorChallengeDTO> challenges;
    private List<CreatorChallengeBadgeDTO> badges;
    private String boundaryCopy;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CreatorChallengeDTO {
        private Long id;
        private String challengeCode;
        private String title;
        private String description;
        private Integer domain;
        private Integer postType;
        private String assistTemplateCode;
        private String status;
        private LocalDateTime startsAt;
        private LocalDateTime endsAt;
        private String participationStatus;
        private LocalDateTime joinedAt;
        private LocalDateTime completedAt;
        private Long completedPostId;
        private List<EligiblePostDTO> eligiblePosts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EligiblePostDTO {
        private Long postId;
        private String title;
        private Integer domain;
        private Integer postType;
        private LocalDateTime publishedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CreatorChallengeBadgeDTO {
        private String badgeCode;
        private String title;
        private String description;
        private Integer requiredCompletedChallengeCount;
        private LocalDateTime awardedAt;
        private String boundaryCopy;
    }
}
