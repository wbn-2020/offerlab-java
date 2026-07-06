package com.offerlab.community.post.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class OperationTopicCmd {
    @Size(max = 64)
    private String slug;
    @Size(max = 64)
    private String name;
    @Size(max = 500)
    private String description;
    private String operationType;
    @Size(max = 512)
    private String coverUrl;
    private Integer domain;
    private String status;
    private Integer sortOrder;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    @Size(max = 500)
    private String note;
    @Size(max = 20)
    private List<@Valid OperationTopicSectionCmd> sections;
}
