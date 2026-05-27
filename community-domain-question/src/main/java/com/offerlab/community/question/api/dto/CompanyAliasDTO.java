package com.offerlab.community.question.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CompanyAliasDTO {
    private Long id;
    private String canonicalCompany;
    private String alias;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
