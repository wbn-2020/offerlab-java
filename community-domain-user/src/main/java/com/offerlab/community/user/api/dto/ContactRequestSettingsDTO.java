package com.offerlab.community.user.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactRequestSettingsDTO {
    private Boolean acceptContactRequest;
    private String contactRequestPolicy;
    private Integer contactRequestDailyLimit;
}
