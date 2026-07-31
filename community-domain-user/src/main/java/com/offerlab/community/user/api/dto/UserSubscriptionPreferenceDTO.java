package com.offerlab.community.user.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSubscriptionPreferenceDTO {

    private String sourceType;
    private Long sourceId;
    private String deliveryMode;
    private LocalDateTime expiresAt;
}
