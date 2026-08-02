package com.offerlab.community.analytics.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorChallengeCompletionResultDTO {
    private CreatorChallengeWorkspaceDTO.CreatorChallengeDTO challenge;
    private List<CreatorChallengeWorkspaceDTO.CreatorChallengeBadgeDTO> newlyAwardedBadges;
    private boolean replayed;
}
