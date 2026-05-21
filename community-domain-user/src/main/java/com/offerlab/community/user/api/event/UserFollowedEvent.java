package com.offerlab.community.user.api.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户被关注事件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFollowedEvent {
    private Long followerId;
    private Long followeeId;
    private Long timestamp;
}
