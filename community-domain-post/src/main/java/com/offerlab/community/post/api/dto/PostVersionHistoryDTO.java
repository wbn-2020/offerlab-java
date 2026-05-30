package com.offerlab.community.post.api.dto;

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
public class PostVersionHistoryDTO {
    private Long id;
    private Long postId;
    private Long authorId;
    private Long editorUid;
    private Integer baseVersion;
    private String title;
    private String content;
    private String contentSummary;
    private String coverUrl;
    private Integer visibility;
    private Integer postStatus;
    private String extJson;
    private List<TagDTO> tags;
    private String changeSummary;
    private LocalDateTime createTime;
}
