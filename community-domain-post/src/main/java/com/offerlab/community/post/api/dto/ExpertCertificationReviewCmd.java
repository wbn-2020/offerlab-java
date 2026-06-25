package com.offerlab.community.post.api.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ExpertCertificationReviewCmd {
    private Boolean approved;
    @Size(max = 500)
    private String note;
}
