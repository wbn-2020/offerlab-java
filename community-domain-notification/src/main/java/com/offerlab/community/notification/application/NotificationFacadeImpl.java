package com.offerlab.community.notification.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.notification.api.NotificationFacade;
import com.offerlab.community.notification.infrastructure.persistence.mapper.NotificationMessageMapper;
import com.offerlab.community.notification.infrastructure.persistence.po.NotificationMessagePO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationFacadeImpl implements NotificationFacade {

    private static final int TYPE_LIKE = 1;
    private static final int TYPE_COMMENT = 2;
    private static final int TYPE_FAVORITE = 3;
    private static final int TYPE_FOLLOWER = 4;
    private static final int TYPE_SYSTEM = 5;
    private static final int TYPE_MENTION = 6;

    private static final int TARGET_POST = 1;
    private static final int TARGET_COMMENT = 2;
    private static final int TARGET_USER = 3;

    private final NotificationMessageMapper mapper;
    private final SnowflakeIdGenerator idGen;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<Map<String, Object>> listNotifications(Long uid, String type, long cursor, int size) {
        int limit = clampPageSize(size);
        LambdaQueryWrapper<NotificationMessagePO> q = new LambdaQueryWrapper<NotificationMessagePO>()
                .eq(NotificationMessagePO::getReceiverUid, uid)
                .eq(NotificationMessagePO::getIsDeleted, 0)
                .orderByDesc(NotificationMessagePO::getCreateTime)
                .last("LIMIT " + limit);
        Integer notifType = parseType(type);
        if (notifType != null) {
            q.eq(NotificationMessagePO::getNotifType, notifType);
        }
        if (cursor > 0) {
            q.lt(NotificationMessagePO::getCreateTime,
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(cursor), ZoneOffset.UTC));
        }
        List<NotificationMessagePO> rows = mapper.selectList(q);
        if (rows.isEmpty()) return PageResult.empty();
        List<Map<String, Object>> items = rows.stream().map(this::toItem).toList();
        boolean hasMore = rows.size() == limit;
        String next = hasMore && rows.get(rows.size() - 1).getCreateTime() != null
                ? String.valueOf(rows.get(rows.size() - 1).getCreateTime().toInstant(ZoneOffset.UTC).toEpochMilli())
                : null;
        return PageResult.of(items, next, hasMore);
    }

    @Override
    public long getUnreadCount(Long uid) {
        Long count = mapper.selectCount(baseUnread(uid));
        return count == null ? 0L : count;
    }

    @Override
    public Map<String, Long> getUnreadCountByType(Long uid) {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("total", getUnreadCount(uid));
        result.put("like", countByType(uid, TYPE_LIKE));
        result.put("comment", countByType(uid, TYPE_COMMENT));
        result.put("favorite", countByType(uid, TYPE_FAVORITE));
        result.put("follower", countByType(uid, TYPE_FOLLOWER));
        result.put("mention", countByType(uid, TYPE_MENTION));
        result.put("system", countByType(uid, TYPE_SYSTEM));
        return result;
    }

    @Override
    @Transactional
    public void markAsRead(Long uid, List<Long> notifIds) {
        if (notifIds == null || notifIds.isEmpty()) {
            return;
        }
        mapper.update(null, new LambdaUpdateWrapper<NotificationMessagePO>()
                .eq(NotificationMessagePO::getReceiverUid, uid)
                .eq(NotificationMessagePO::getIsDeleted, 0)
                .in(NotificationMessagePO::getId, notifIds)
                .set(NotificationMessagePO::getIsRead, 1));
    }

    @Override
    @Transactional
    public void markAllAsRead(Long uid) {
        mapper.update(null, new LambdaUpdateWrapper<NotificationMessagePO>()
                .eq(NotificationMessagePO::getReceiverUid, uid)
                .eq(NotificationMessagePO::getIsDeleted, 0)
                .eq(NotificationMessagePO::getIsRead, 0)
                .set(NotificationMessagePO::getIsRead, 1));
    }

    @Override
    @Transactional
    public void notifyLike(Long receiverUid, Long senderUid, Integer targetType, Long targetId) {
        create(receiverUid, senderUid, TYPE_LIKE, targetType, targetId,
                Map.of("action", "like", "targetType", targetType, "targetId", targetId));
    }

    @Override
    @Transactional
    public void notifyCommentLike(Long receiverUid, Long senderUid, Long postId, Long commentId) {
        create(receiverUid, senderUid, TYPE_LIKE, TARGET_COMMENT, commentId,
                Map.of("action", "like", "targetType", TARGET_COMMENT, "targetId", commentId,
                        "postId", postId, "commentId", commentId));
    }

    @Override
    @Transactional
    public void notifyComment(Long receiverUid, Long senderUid, Long postId, Long commentId) {
        create(receiverUid, senderUid, TYPE_COMMENT, TARGET_COMMENT, commentId,
                Map.of("action", "comment", "postId", postId, "commentId", commentId));
    }

    @Override
    @Transactional
    public void notifyFollower(Long receiverUid, Long senderUid) {
        create(receiverUid, senderUid, TYPE_FOLLOWER, TARGET_USER, senderUid,
                Map.of("action", "follow", "userId", senderUid));
    }

    @Override
    @Transactional
    public void notifyFavorite(Long receiverUid, Long senderUid, Long postId) {
        create(receiverUid, senderUid, TYPE_FAVORITE, TARGET_POST, postId,
                Map.of("action", "favorite", "postId", postId));
    }

    @Override
    @Transactional
    public void notifyMention(Long receiverUid, Long senderUid, Long postId, Long commentId) {
        Map<String, Object> content = commentId == null
                ? Map.of("action", "mention", "postId", postId)
                : Map.of("action", "mention", "postId", postId, "commentId", commentId);
        create(receiverUid, senderUid, TYPE_MENTION, commentId == null ? TARGET_POST : TARGET_COMMENT,
                commentId == null ? postId : commentId, content);
    }

    private void create(Long receiverUid, Long senderUid, Integer notifType,
                        Integer targetType, Long targetId, Map<String, Object> content) {
        if (receiverUid == null || senderUid == null || receiverUid.equals(senderUid)) {
            return;
        }
        NotificationMessagePO po = new NotificationMessagePO();
        po.setId(idGen.nextId());
        po.setReceiverUid(receiverUid);
        po.setSenderUid(senderUid);
        po.setNotifType(notifType);
        po.setTargetType(targetType);
        po.setTargetId(targetId);
        po.setContentJson(toJson(content));
        po.setIsRead(0);
        po.setIsDeleted(0);
        mapper.insert(po);
    }

    private LambdaQueryWrapper<NotificationMessagePO> baseUnread(Long uid) {
        return new LambdaQueryWrapper<NotificationMessagePO>()
                .eq(NotificationMessagePO::getReceiverUid, uid)
                .eq(NotificationMessagePO::getIsRead, 0)
                .eq(NotificationMessagePO::getIsDeleted, 0);
    }

    private long countByType(Long uid, int type) {
        Long count = mapper.selectCount(baseUnread(uid).eq(NotificationMessagePO::getNotifType, type));
        return count == null ? 0L : count;
    }

    private Map<String, Object> toItem(NotificationMessagePO po) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", po.getId());
        item.put("receiverUid", po.getReceiverUid());
        item.put("senderUid", po.getSenderUid());
        item.put("type", typeName(po.getNotifType()));
        item.put("notifType", po.getNotifType());
        item.put("targetType", po.getTargetType());
        item.put("targetId", po.getTargetId());
        item.put("content", parseContent(po.getContentJson()));
        item.put("isRead", po.getIsRead() != null && po.getIsRead() == 1);
        item.put("createTime", po.getCreateTime());
        return item;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseContent(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String toJson(Map<String, Object> content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            log.warn("serialize notification content failed: {}", e.getMessage());
            return "{}";
        }
    }

    private Integer parseType(String type) {
        if (type == null || type.isBlank() || "all".equalsIgnoreCase(type)) return null;
        return switch (type.toLowerCase()) {
            case "like" -> TYPE_LIKE;
            case "comment" -> TYPE_COMMENT;
            case "favorite" -> TYPE_FAVORITE;
            case "follower", "follow" -> TYPE_FOLLOWER;
            case "mention" -> TYPE_MENTION;
            case "system" -> TYPE_SYSTEM;
            default -> null;
        };
    }

    private String typeName(Integer type) {
        if (type == null) return "system";
        return switch (type) {
            case TYPE_LIKE -> "like";
            case TYPE_COMMENT -> "comment";
            case TYPE_FAVORITE -> "favorite";
            case TYPE_FOLLOWER -> "follower";
            case TYPE_MENTION -> "mention";
            default -> "system";
        };
    }

    private int clampPageSize(int size) {
        return Math.max(1, Math.min(size, 50));
    }
}
