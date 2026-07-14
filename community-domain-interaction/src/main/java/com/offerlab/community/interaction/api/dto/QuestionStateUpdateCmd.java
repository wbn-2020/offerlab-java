package com.offerlab.community.interaction.api.dto;

import com.offerlab.community.interaction.api.enums.QuestionStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class QuestionStateUpdateCmd {
    @NotNull
    private QuestionStatus status;
    @Positive
    private Long duplicatePostId;
}
