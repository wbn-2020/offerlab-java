package com.offerlab.community.post.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.offerlab.community.user.api.dto.UserBriefDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostDTO {
    private Long id;
    @JsonIgnore
    private Long authorId;
    private UserBriefDTO author;
    private Integer postType;
    private String title;
    private String content;
    private String coverUrl;
    @JsonIgnore
    private Integer visibility;
    @JsonIgnore
    private Integer postStatus;
    @JsonIgnore
    private String contentEnvironment;
    private String extJson;
    private Integer domain;
    private Boolean anonymous;
    private List<TagDTO> tags;
    private PostCounterDTO counter;
    private PostTrustSignalsDTO trustSignals;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
