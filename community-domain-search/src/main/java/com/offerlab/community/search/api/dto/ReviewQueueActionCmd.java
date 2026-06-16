package com.offerlab.community.search.api.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReviewQueueActionCmd {
    @Size(max = 500)
    private String note;
    @Size(max = 32)
    private String confirmationPhrase;
}
