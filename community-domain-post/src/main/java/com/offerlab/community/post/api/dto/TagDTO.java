package com.offerlab.community.post.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagDTO {
    private Long id;
    private String name;
    private String slug;
    private String category;
    private Integer tagType;
    private Long useCount;
    private Long postCount;
    private Map<String, Long> typeDistribution;
    private Boolean statisticsAvailable;
    private Boolean official;
    private Integer status;
    private Boolean recommended;
    private Long mergeTargetId;
    private java.util.List<String> synonyms;
}
