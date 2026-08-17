package com.offerlab.community.post.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityTopicDTO {
    private Long id;
    private String slug;
    private String name;
    private String description;
    private String topicType;
    private String coverUrl;
    private Integer sortOrder;
    private Boolean featured;
    private Integer status;
    private Long postCount;
    private Map<String, Long> typeDistribution;
    private Boolean statisticsAvailable;
    private Long followerCount;
    private Boolean followed;
    private Boolean virtualTopic;
    private List<TagDTO> tags;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
