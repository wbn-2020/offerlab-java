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
public class QuestionDetailDTO {
    private QuestionDTO question;
    private List<PostBriefDTO> sourcePosts;
    private List<QuestionDTO> relatedQuestions;
}
