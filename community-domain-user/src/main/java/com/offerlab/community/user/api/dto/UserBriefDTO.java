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
    /** 当前请求人是否可查看完整主页资料 */
    private Boolean profileVisible;
    /** 当前请求人是否可查看求职意向 */
    private Boolean intentVisible;
    /** 资料受限时给前端展示的原因 */
    private String privacyReason;
}
