package com.offerlab.community.post.api.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class TagGovernanceCmd {
    private String name;
    private Integer tagType;
    private Integer status;
    private Boolean recommended;
    private Long mergeTargetId;
    private List<String> synonyms;
    @Size(max = 500)
    private String note;
    @Size(max = 32)
    private String confirmationPhrase;
}
