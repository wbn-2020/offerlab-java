package com.offerlab.community.interaction.api.dto;

import com.offerlab.community.interaction.api.enums.ContentSuggestionTargetScope;
import com.offerlab.community.interaction.api.enums.ContentSuggestionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContentSuggestionSubmitCmd {
    @NotNull
    private ContentSuggestionType type;
    @NotBlank
    @Size(max = 2000)
    private String detail;
    @Size(max = 1000)
    private String sourceUrl;
    private ContentSuggestionTargetScope targetScope;
    @Size(max = 255)
    private String targetLocator;
    @Size(max = 2000)
    private String expectedChange;
    private Boolean allowPublicAttribution;
}
