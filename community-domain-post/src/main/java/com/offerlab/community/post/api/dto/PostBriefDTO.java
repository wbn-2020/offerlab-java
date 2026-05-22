package com.offerlab.community.post.api.dto;

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
public class PostBriefDTO {
    private Long id;
    private Long authorId;
    private UserBriefDTO author;
    private Integer postType;
    private String title;
    private String summary;
    private String coverUrl;
    private String extJson;
    private List<TagDTO> tags;
    private PostCounterDTO counter;
    private LocalDateTime createTime;
}
