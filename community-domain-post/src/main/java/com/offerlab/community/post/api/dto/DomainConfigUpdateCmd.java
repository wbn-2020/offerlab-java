package com.offerlab.community.post.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DomainConfigUpdateCmd {
    @Size(max = 64)
    private String domainName;
    @Size(max = 32)
    private String domainSlug;
    @Size(max = 500)
    private String description;
    @Min(0)
    @Max(9999)
    private Integer sortOrder;
    private Boolean enabled;
    @Size(max = 16)
    private String riskLevel;
    @Size(max = 500)
    private String postingNotice;
    @Size(max = 500)
    private String browseNotice;
    @Size(max = 500)
    private String interactionNotice;
    @Size(max = 500)
    private String note;
}
