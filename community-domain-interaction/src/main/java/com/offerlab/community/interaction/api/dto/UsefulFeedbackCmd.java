package com.offerlab.community.interaction.api.dto;

import com.offerlab.community.interaction.api.enums.UsefulFeedbackReason;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UsefulFeedbackCmd {
    @NotNull
    private UsefulFeedbackReason reason;
}
