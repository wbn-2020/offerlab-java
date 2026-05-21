package com.offerlab.community.notification.application;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.notification.api.NotificationFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * MVP 阶段占位：返回空结果，二期实现完整通知体系
 */
@Slf4j
@Service
public class NotificationFacadeImpl implements NotificationFacade {

    @Override
    public PageResult<Map<String, Object>> listNotifications(Long uid, String type, long cursor, int size) {
        return PageResult.empty();
    }

    @Override
    public long getUnreadCount(Long uid) {
        return 0L;
    }

    @Override
    public void markAsRead(Long uid, List<Long> notifIds) {
    }

    @Override
    public void markAllAsRead(Long uid) {
    }
}
