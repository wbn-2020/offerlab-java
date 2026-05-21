package com.offerlab.community.user.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户聚合根：账号 + 资料合一的领域视图
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    public static final int STATUS_NORMAL = 1;
    public static final int STATUS_BANNED = 2;
    public static final int STATUS_INACTIVE = 3;

    private Long id;
    private String email;
    private String passwordHash;
    private Integer accountStatus;
    private LocalDateTime lastLoginTime;

    private String nickname;
    private String avatarUrl;
    private String bio;
    private String intentJson;

    private LocalDateTime createTime;

    public boolean isActive() {
        return accountStatus != null && accountStatus == STATUS_NORMAL;
    }
}
