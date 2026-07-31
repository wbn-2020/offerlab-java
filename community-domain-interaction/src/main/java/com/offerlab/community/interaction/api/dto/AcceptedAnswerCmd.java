package com.offerlab.community.interaction.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class AcceptedAnswerCmd {
    @NotNull
    @Positive
    private Long commentId;
}
