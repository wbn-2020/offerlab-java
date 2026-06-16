package com.offerlab.community.question.api.dto;

import com.offerlab.community.post.api.dto.PostBriefDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserKnowledgeDTO {
    private Long materialPackCount;
    private Long savedMaterialPackCount;
    private Long favoritePostCount;
    private Long favoriteQuestionCount;
    private List<InterviewMaterialPackDTO> materialPacks;
    private List<PostBriefDTO> favoritePosts;
    private List<QuestionDTO> favoriteQuestions;
    private List<PrepTargetDTO> targets;
    private List<UserPrepOverviewDTO.FocusTagCountDTO> weakTags;
    private List<String> materialGapHints;
}
