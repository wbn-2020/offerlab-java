package com.offerlab.community.notification.controller;

import com.offerlab.community.user.api.dto.UserBriefDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationListItemResponse {
    private Long id;
    private UserBriefDTO sender;
    private String type;
    private Map<String, Object> content;
    private Boolean isRead;
    private LocalDateTime createTime;
    private List<Long> notificationIds;
    private Integer aggregateCount;
    private Integer unreadCount;
}
