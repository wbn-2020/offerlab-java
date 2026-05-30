package com.offerlab.community.question.api.dto;

import com.offerlab.community.post.api.dto.PostBriefDTO;
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
public class CompanyPrepDTO {
    private String company;
    private List<String> aliases;
    private Integer relatedPositionCount;
    private List<PostBriefDTO> recentPosts;
    private List<QuestionDTO> topQuestions;
    private List<QuestionDTO> recommendedQuestions;
    private List<NameCountDTO> topTags;
    private List<NameCountDTO> hotPositions;
    private List<NameCountDTO> trend30Days;
    private List<NameCountDTO> trend90Days;
    private List<NameCountDTO> interviewResultDistribution;
    private List<NameCountDTO> recentResultDistribution;
    private Long questionSampleCount;
    private Long postSampleCount;
    private Long resultSampleCount;
    private Long recentResultSampleCount;
    private LocalDateTime dataUpdatedAt;
    private UserPrepSummaryDTO myProgress;
    private Integer prepScore;
    private List<ChecklistItemDTO> checklist;
    private List<String> nextActions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NameCountDTO {
        private String name;
        private Long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserPrepSummaryDTO {
        private Long favoriteCount;
        private Long learningCount;
        private Long masteredCount;
        private Long reviewCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChecklistItemDTO {
        private String key;
        private String title;
        private String description;
        private Boolean done;
        private Integer current;
        private Integer target;
        private String actionHref;
    }
}
