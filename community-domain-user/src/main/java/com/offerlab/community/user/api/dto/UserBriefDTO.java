package com.offerlab.community.user.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBriefDTO {
    private Long uid;
    private String nickname;
    private String avatarUrl;
    private String bio;
    private Long followerCount;
    private Long followingCount;
    private Long postCount;
    /** 当前请求人是否已关注此用户（不需要时为 null） */
    private Boolean isFollowing;
}
