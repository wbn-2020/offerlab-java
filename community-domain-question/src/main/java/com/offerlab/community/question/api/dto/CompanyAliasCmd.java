package com.offerlab.community.question.api.dto;

import lombok.Data;

@Data
public class CompanyAliasCmd {
    private String canonicalCompany;
    private String alias;
    private Integer status;
}
