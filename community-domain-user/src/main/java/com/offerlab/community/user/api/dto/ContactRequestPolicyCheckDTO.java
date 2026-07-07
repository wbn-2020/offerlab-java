package com.offerlab.community.user.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactRequestPolicyCheckDTO {
    private Boolean allowed;
    private String reasonCode;
    private String reasonMessage;
    private String contactRequestPolicy;
    private Integer contactRequestDailyLimit;
}
