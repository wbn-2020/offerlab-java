package com.offerlab.community.interaction.api.dto;

import com.offerlab.community.interaction.api.enums.FreshnessStatus;
import com.offerlab.community.interaction.api.enums.QuestionStatus;
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
public class TrustedContentDTO {
    private Long postId;
    private QuestionStatus questionStatus;
    private List<QuestionStatus> allowedQuestionStatuses;
    private FreshnessStatus freshnessStatus;
    private Long acceptedCommentId;
    private Long duplicatePostId;
    private Long successorPostId;
    private LocalDateTime lastConfirmedAt;
    private Boolean suggestionsOpen;
    private UsefulFeedbackSummaryDTO usefulFeedback;
    private List<PublicContentSuggestionDTO> publicSuggestionRecords;
}
