package com.offerlab.community.question.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CompanyAliasCandidateDTO {
    private String canonicalCompany;
    private String alias;
    private Long canonicalSampleCount;
    private Long aliasSampleCount;
    private Long questionSampleCount;
    private Long postSampleCount;
    private Long totalSampleCount;
    private String reason;
    private List<String> sampleCompanies;
}
