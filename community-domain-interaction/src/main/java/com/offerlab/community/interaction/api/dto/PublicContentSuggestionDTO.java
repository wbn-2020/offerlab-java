package com.offerlab.community.interaction.api.dto;

import com.offerlab.community.interaction.api.enums.ContentSuggestionDecision;
import com.offerlab.community.interaction.api.enums.ContentSuggestionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicContentSuggestionDTO {
    private Long suggestionId;
    private ContentSuggestionType type;
    private ContentSuggestionDecision decision;
    private String publicNote;
    private Integer resultVersion;
    private LocalDateTime decidedAt;
    private String submitterNickname;
}
