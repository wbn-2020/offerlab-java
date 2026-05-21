package com.offerlab.community.post.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Post {

    public static final int TYPE_INTERVIEW = 1;
    public static final int TYPE_BLOG = 2;
    public static final int TYPE_SOLUTION = 3;
    public static final int TYPE_QA = 4;

    public static final int VIS_PUBLIC = 1;
    public static final int VIS_SELF = 2;
    public static final int VIS_FOLLOWER = 3;

    public static final int STATUS_PUBLISHED = 1;
    public static final int STATUS_DRAFT = 2;
    public static final int STATUS_REVIEWING = 3;
    public static final int STATUS_TAKEN_DOWN = 4;

    private Long id;
    private Long authorId;
    private Integer postType;
    private String title;
    private String content;
    private String coverUrl;
    private Integer visibility;
    private Integer postStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    /** 扩展字段 JSON（公司/岗位/年限/结果 等） */
    private String extJson;

    public boolean isVisibleTo(Long viewerUid, boolean isFollowing) {
        if (postStatus == null || postStatus != STATUS_PUBLISHED) return false;
        if (visibility == null || visibility == VIS_PUBLIC) return true;
        if (viewerUid == null) return false;
        if (visibility == VIS_SELF) return authorId.equals(viewerUid);
        if (visibility == VIS_FOLLOWER) return authorId.equals(viewerUid) || isFollowing;
        return false;
    }
}
