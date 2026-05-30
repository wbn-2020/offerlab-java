package com.offerlab.community.question.api.event;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuestionExtractionFinishedEvent {
    private Long taskId;
    private Long postId;
    private Long postAuthorUid;
    private String postTitle;
    private boolean success;
    private int questionCount;
    private String errorMessage;
    private long timestamp;
}
