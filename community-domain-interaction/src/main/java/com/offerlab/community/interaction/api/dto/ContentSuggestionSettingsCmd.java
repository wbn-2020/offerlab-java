package com.offerlab.community.interaction.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ContentSuggestionSettingsCmd {
    @NotNull
    private Boolean suggestionsOpen;
}
