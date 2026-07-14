package com.offerlab.community.interaction.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.offerlab.community.interaction.api.enums.ContentSuggestionDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContentSuggestionDecisionCmd {
    @NotNull
    private ContentSuggestionDecision decision;
    @JsonAlias({"reason", "decisionReason"})
    @Size(max = 1000)
    private String authorReply;
    @Size(max = 500)
    private String publicNote;
}
