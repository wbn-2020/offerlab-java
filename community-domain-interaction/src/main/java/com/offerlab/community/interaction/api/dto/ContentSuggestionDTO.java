package com.offerlab.community.interaction.api.dto;

import com.offerlab.community.interaction.api.enums.ContentSuggestionDecision;
import com.offerlab.community.interaction.api.enums.ContentSuggestionDeliveryStatus;
import com.offerlab.community.interaction.api.enums.ContentSuggestionResolution;
import com.offerlab.community.interaction.api.enums.ContentSuggestionStatus;
import com.offerlab.community.interaction.api.enums.ContentSuggestionTargetScope;
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
public class ContentSuggestionDTO {
    private Long id;
    private Long postId;
    private Long postAuthorUid;
    private Long submitterUid;
    private String submitterNickname;
    private ContentSuggestionType type;
    private String detail;
    private String sourceUrl;
    private Integer baseVersion;
    private ContentSuggestionTargetScope targetScope;
    private String targetLocator;
    private String expectedChange;
    private Boolean allowPublicAttribution;
    private ContentSuggestionStatus status;
    private ContentSuggestionDecision decision;
    private ContentSuggestionResolution resolution;
    private ContentSuggestionDeliveryStatus deliveryStatus;
    private String authorReply;
    private String publicNote;
    private Integer resultVersion;
    private LocalDateTime decidedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
