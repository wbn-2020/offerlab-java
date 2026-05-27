package com.offerlab.community.question.application;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuestionExtractRequestedEvent {
    private Long taskId;
    private Long postId;
    private boolean manual;
}
