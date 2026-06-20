package com.offerlab.community.post.domain.model;

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
public class Post {

    public static final int TYPE_INTERVIEW = 1;
    public static final int TYPE_BLOG = 2;
    public static final int TYPE_SOLUTION = 3;
    public static final int TYPE_QA = 4;
    public static final int TYPE_TECH_ARTICLE = 10;
    public static final int TYPE_PROJECT_REVIEW = 11;
    public static final int TYPE_PITFALL = 12;
    public static final int TYPE_COMMUNITY_QUESTION = 13;
    public static final int TYPE_RESOURCE = 14;
    public static final int TYPE_NOTE = 15;
    public static final int TYPE_SYSTEM_DESIGN = 16;
    public static final int TYPE_INTERVIEW_RECAP = 17;

    public static final int DOMAIN_TECH = 1;
    public static final int DOMAIN_CAREER = 2;
    public static final int DOMAIN_READING = 3;
    public static final int DOMAIN_LIFESTYLE = 4;
    public static final int DOMAIN_INVESTMENT = 5;

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
    private Integer version;
    /** 领域编码，对应 PostDomain 枚举。存于 extJson 中，取值参考 DOMAIN_* 常量 */
    private Integer domain;
    private List<Long> tagIds;

    public static boolean isSupportedType(Integer postType) {
        if (postType == null) return false;
        return postType == TYPE_INTERVIEW
                || postType == TYPE_BLOG
                || postType == TYPE_SOLUTION
                || postType == TYPE_QA
                || postType == TYPE_TECH_ARTICLE
                || postType == TYPE_PROJECT_REVIEW
                || postType == TYPE_PITFALL
                || postType == TYPE_COMMUNITY_QUESTION
                || postType == TYPE_RESOURCE
                || postType == TYPE_NOTE
                || postType == TYPE_SYSTEM_DESIGN
                || postType == TYPE_INTERVIEW_RECAP;
    }

    public static boolean isInterviewType(Integer postType) {
        return postType != null && postType == TYPE_INTERVIEW;
    }

    public boolean isVisibleTo(Long viewerUid, boolean isFollowing) {
        if (postStatus == null || postStatus != STATUS_PUBLISHED) return false;
        if (visibility == null || visibility == VIS_PUBLIC) return true;
        if (viewerUid == null) return false;
        if (visibility == VIS_SELF) return authorId.equals(viewerUid);
        if (visibility == VIS_FOLLOWER) return authorId.equals(viewerUid) || isFollowing;
        return false;
    }
}
