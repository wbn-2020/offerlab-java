package com.offerlab.community.interaction.api.dto;

import com.offerlab.community.interaction.api.enums.PostOutcomeType;
import com.offerlab.community.interaction.api.enums.PostOutcomeVisibility;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PostOutcomeCmd {
    @NotNull
    private PostOutcomeType outcomeType;
    @Size(max = 1000)
    private String contextNote;
    @Size(max = 2000)
    private String resultNote;
    private PostOutcomeVisibility visibility;
    private LocalDateTime followUpAt;
    @Positive
    private Integer expectedRevision;
    private Boolean riskAcknowledged;
}
