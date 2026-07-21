package com.offerlab.community.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSubscriptionPreferenceUpdateCmd {

    @NotBlank
    @Size(max = 16)
    private String deliveryMode;

    private OffsetDateTime expiresAt;
}
