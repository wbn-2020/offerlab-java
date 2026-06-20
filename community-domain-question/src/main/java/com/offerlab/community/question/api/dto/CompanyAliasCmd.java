package com.offerlab.community.question.api.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CompanyAliasCmd {
    private String canonicalCompany;
    private String alias;
    private Integer status;
    @Size(max = 500)
    private String remark;
}
