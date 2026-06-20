package com.offerlab.community.post.api.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityTopicCmd {
    @Size(max = 64)
    private String slug;
    @Size(max = 64)
    private String name;
    @Size(max = 500)
    private String description;
    @Size(max = 32)
    private String topicType;
    @Size(max = 512)
    private String coverUrl;
    private Integer sortOrder;
    private Boolean featured;
    private Integer status;
    private List<Long> tagIds;
    private List<String> tagNames;
    @Size(max = 500)
    private String note;
}
