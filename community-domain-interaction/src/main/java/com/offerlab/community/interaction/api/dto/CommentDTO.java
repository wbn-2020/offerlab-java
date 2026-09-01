package com.offerlab.community.interaction.api.dto;

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
public class CommentDTO {
    private Long id;
    private Long postId;
    @JsonIgnore
    private Long authorId;
    private UserBriefDTO author;
    private Long rootId;
    private Long parentId;
    private Long replyToUid;
    private UserBriefDTO replyToUser;
    private String content;
    private Integer likeCount;
    private Boolean myLiked;
    private Boolean authorReply;
    private Boolean authorPinned;
    private Boolean featured;
    private Integer helpfulCount;
    private Boolean myHelpful;
    private Double hotScore;
    private Boolean folded;
    private String foldReason;
    private List<String> qualityBadges;
    private Boolean canDelete;
    private Integer replyCount;
    private Boolean hasMoreReplies;
    private String repliesNextCursor;
    private List<CommentDTO> replies;
    private LocalDateTime createTime;
}
