package com.offerlab.community.post.api.dto;

import com.offerlab.community.user.api.dto.UserBriefDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Complete internal snapshot for the raw post-detail cache.
 *
 * PostDTO is also serialized at public HTTP boundaries and intentionally
 * hides visibility metadata. The cache must retain that metadata for later
 * visibility checks from other modules.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostDetailCacheDTO {
    private Long id;
    private Long authorId;
    private UserBriefDTO author;
    private Integer postType;
    private String title;
    private String content;
    private String coverUrl;
    private Integer visibility;
    private Integer postStatus;
    private String contentEnvironment;
    private String extJson;
    private Integer domain;
    private Boolean anonymous;
    private List<TagDTO> tags;
    private PostCounterDTO counter;
    private PostTrustSignalsDTO trustSignals;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static PostDetailCacheDTO from(PostDTO source) {
        if (source == null) {
            return null;
        }
        return PostDetailCacheDTO.builder()
                .id(source.getId())
                .authorId(source.getAuthorId())
                .author(source.getAuthor())
                .postType(source.getPostType())
                .title(source.getTitle())
                .content(source.getContent())
                .coverUrl(source.getCoverUrl())
                .visibility(source.getVisibility())
                .postStatus(source.getPostStatus())
                .contentEnvironment(source.getContentEnvironment())
                .extJson(source.getExtJson())
                .domain(source.getDomain())
                .anonymous(source.getAnonymous())
                .tags(source.getTags())
                .counter(source.getCounter())
                .trustSignals(source.getTrustSignals())
                .createTime(source.getCreateTime())
                .updateTime(source.getUpdateTime())
                .build();
    }

    public PostDTO toPostDTO() {
        return PostDTO.builder()
                .id(id)
                .authorId(authorId)
                .author(author)
                .postType(postType)
                .title(title)
                .content(content)
                .coverUrl(coverUrl)
                .visibility(visibility)
                .postStatus(postStatus)
                .contentEnvironment(contentEnvironment)
                .extJson(extJson)
                .domain(domain)
                .anonymous(anonymous)
                .tags(tags)
                .counter(counter)
                .trustSignals(trustSignals)
                .createTime(createTime)
                .updateTime(updateTime)
                .build();
    }

    public boolean hasVisibilityMetadata() {
        return id != null
                && contentEnvironment != null
                && !contentEnvironment.isBlank()
                && postStatus != null;
    }
}
