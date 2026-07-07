package com.offerlab.community.notification.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.utils.LogMask;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.notification.api.NotificationFacade;
import com.offerlab.community.notification.api.dto.NotificationRealtimeStatusDTO;
import com.offerlab.community.notification.infrastructure.persistence.mapper.NotificationMessageMapper;
import com.offerlab.community.notification.infrastructure.persistence.po.NotificationMessagePO;
import com.offerlab.community.user.api.UserFacade;
import com.offerlab.community.user.api.dto.UserBriefDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationFacadeImpl implements NotificationFacade {

    private static final int REALTIME_POLL_INTERVAL_SECONDS = 20;
    private static final long AGGREGATION_WINDOW_MINUTES = 30L;
    private static final int MAX_READ_BATCH_SIZE = 200;

    private static final int TYPE_LIKE = 1;
    private static final int TYPE_COMMENT = 2;
    private static final int TYPE_FAVORITE = 3;
    private static final int TYPE_FOLLOWER = 4;
    private static final int TYPE_SYSTEM = 5;
    private static final int TYPE_MENTION = 6;

    private static final int TARGET_POST = 1;
    private static final int TARGET_COMMENT = 2;
    private static final int TARGET_USER = 3;
    private static final String ACTION_REPORT_RECEIPT = "report_receipt";
    private static final String ACTION_CONTACT_REQUEST_RECEIVED = "contact_request_received";
    private static final String ACTION_CONTACT_REQUEST_ACCEPTED = "contact_request_accepted";
    private static final String ACTION_CONTACT_REQUEST_REJECTED = "contact_request_rejected";
    private static final String REPORT_USER_STATUS_ACTION_TAKEN = "ACTION_TAKEN";
    private static final String REPORT_USER_STATUS_NOT_ACCEPTED = "NOT_ACCEPTED";
    private static final String REPORT_USER_STATUS_CLOSED = "CLOSED";
    private static final String CONTACT_REQUEST_INBOX_PATH = "/me/contact-requests?tab=inbox";
    private static final String CONTACT_REQUEST_OUTBOX_PATH = "/me/contact-requests?tab=outbox";

    private final NotificationMessageMapper mapper;
    private final SnowflakeIdGenerator idGen;
    private final ObjectMapper objectMapper;
    private final UserFacade userFacade;

    @Value("${offerlab.realtime.websocket-enabled:false}")
    private boolean websocketEnabled;

    @Override
    public PageResult<Map<String, Object>> listNotifications(Long uid, String type, long cursor, int size) {
        if (!messageTableReady()) {
            return PageResult.empty();
        }
        int limit = clampPageSize(size);
        Integer notifType = parseType(type);
        LocalDateTime cursorTime = cursor > 0
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(cursor), ZoneOffset.UTC)
                : null;
        List<NotificationMessagePO> rows = mapper.listByUser(uid, notifType, cursorTime, limit);
        if (rows.isEmpty()) return PageResult.empty();
        Set<Long> senderIds = rows.stream()
                .map(NotificationMessagePO::getSenderUid)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        Map<Long, UserBriefDTO> senders = userFacade.batchGetUserBriefs(senderIds);
        List<Map<String, Object>> items = aggregateItems(rows, senders);
        boolean hasMore = rows.size() == limit;
        String next = hasMore && rows.get(rows.size() - 1).getCreateTime() != null
                ? String.valueOf(rows.get(rows.size() - 1).getCreateTime().toInstant(ZoneOffset.UTC).toEpochMilli())
                : null;
        return PageResult.of(items, next, hasMore);
    }

    @Override
    public long getUnreadCount(Long uid) {
        if (!messageTableReady()) {
            return 0L;
        }
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
    public NotificationRealtimeStatusDTO getRealtimeStatus(Long uid) {
        NotificationMessagePO latestUnread = messageTableReady() ? mapper.selectLatestUnread(uid) : null;
        return NotificationRealtimeStatusDTO.builder()
                .unread(getUnreadCountByType(uid))
                .latestUnreadId(latestUnread == null ? null : latestUnread.getId())
                .latestUnreadAt(latestUnread == null ? null : latestUnread.getCreateTime())
                .serverTime(System.currentTimeMillis())
                .pollIntervalSeconds(REALTIME_POLL_INTERVAL_SECONDS)
                .websocketEnabled(websocketEnabled)
                .build();
    }

    @Override
    @Transactional
    public void markAsRead(Long uid, List<Long> notifIds) {
        List<Long> normalizedNotifIds = normalizeReadIds(notifIds);
        if (normalizedNotifIds.isEmpty() || !messageTableReady()) {
            return;
        }
        mapper.update(null, new LambdaUpdateWrapper<NotificationMessagePO>()
                .eq(NotificationMessagePO::getReceiverUid, uid)
                .eq(NotificationMessagePO::getIsDeleted, 0)
                .in(NotificationMessagePO::getId, normalizedNotifIds)
                .set(NotificationMessagePO::getIsRead, 1));
    }

    @Override
    @Transactional
    public void markAllAsRead(Long uid) {
        if (!messageTableReady()) {
            return;
        }
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
    public void notifyDiscussionFollowComment(Long receiverUid, Long senderUid, Long postId, Long commentId) {
        create(receiverUid, senderUid, TYPE_COMMENT, TARGET_COMMENT, commentId,
                Map.of("action", "discussion_follow_comment",
                        "postId", postId,
                        "commentId", commentId,
                        "targetPath", commentsTargetPath(postId)));
    }

    @Override
    @Transactional
    public void notifyDiscussionFollowQualityComment(Long receiverUid, Long senderUid, Long postId, Long commentId, String action) {
        String normalizedAction = normalizeDiscussionFollowQualityAction(action);
        if (normalizedAction == null) {
            return;
        }
        create(receiverUid, senderUid, TYPE_COMMENT, TARGET_COMMENT, commentId,
                Map.of("action", normalizedAction,
                        "postId", postId,
                        "commentId", commentId,
                        "targetPath", commentsTargetPath(postId),
                        "message", discussionFollowQualityMessage(normalizedAction)));
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

    @Override
    @Transactional
    public void notifySystem(Long receiverUid, Long targetType, Long targetId, Map<String, Object> content) {
        create(receiverUid, 0L, TYPE_SYSTEM, targetType == null ? null : targetType.intValue(), targetId,
                content == null ? Map.of() : content);
    }

    @Override
    @Transactional
    public void notifyReportReceipt(Long receiverUid, String sourceType, Long reportId, String userStatus, String targetPath) {
        create(receiverUid, 0L, TYPE_SYSTEM, null, reportId,
                reportReceiptContent(sourceType, reportId, userStatus, targetPath));
    }

    @Override
    @Transactional
    public void notifyContactRequestReceived(Long receiverUid, Long requesterUid, Long requestId) {
        create(receiverUid, requesterUid, TYPE_SYSTEM, null, requestId,
                contactRequestContent(ACTION_CONTACT_REQUEST_RECEIVED, requestId,
                        CONTACT_REQUEST_INBOX_PATH, "有人发来了联系请求。"));
    }

    @Override
    @Transactional
    public void notifyContactRequestAccepted(Long requesterUid, Long receiverUid, Long requestId) {
        create(requesterUid, receiverUid, TYPE_SYSTEM, null, requestId,
                contactRequestContent(ACTION_CONTACT_REQUEST_ACCEPTED, requestId,
                        CONTACT_REQUEST_OUTBOX_PATH, "你的联系请求已被接受。"));
    }

    @Override
    @Transactional
    public void notifyContactRequestRejected(Long requesterUid, Long receiverUid, Long requestId) {
        create(requesterUid, receiverUid, TYPE_SYSTEM, null, requestId,
                contactRequestContent(ACTION_CONTACT_REQUEST_REJECTED, requestId,
                        CONTACT_REQUEST_OUTBOX_PATH, "你的联系请求已被拒绝。"));
    }

    private void create(Long receiverUid, Long senderUid, Integer notifType,
                        Integer targetType, Long targetId, Map<String, Object> content) {
        if (receiverUid == null || senderUid == null || receiverUid.equals(senderUid)) {
            return;
        }
        if (!messageTableReady()) {
            return;
        }
        if (TYPE_SYSTEM == notifType) {
            if (!userFacade.allowsSystemNotification(receiverUid)) {
                return;
            }
        } else if (!allowsNotificationType(receiverUid, notifType)) {
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
        po.setDedupKey(NotificationDedupKey.of(receiverUid, senderUid, notifType, targetType, targetId, content));
        po.setIsRead(0);
        po.setIsDeleted(0);
        int inserted = dedupKeyColumnReady() ? mapper.insertIgnore(po) : mapper.insertLegacy(po);
        if (inserted <= 0) {
            log.debug("duplicate notification skipped: dedupKey={}", LogMask.key(po.getDedupKey()));
        }
    }

    @Transactional
    void createFromRetryTask(Long receiverUid, Long senderUid, Integer notifType,
                             Integer targetType, Long targetId, Map<String, Object> content) {
        create(receiverUid, senderUid, notifType, targetType, targetId, content == null ? Map.of() : content);
    }

    private boolean allowsNotificationType(Long receiverUid, Integer notifType) {
        if (notifType == null) {
            return userFacade.allowsInteractionNotification(receiverUid);
        }
        return switch (notifType) {
            case TYPE_LIKE -> userFacade.allowsLikeNotification(receiverUid);
            case TYPE_COMMENT -> userFacade.allowsCommentNotification(receiverUid);
            case TYPE_FOLLOWER -> userFacade.allowsFollowNotification(receiverUid);
            case TYPE_FAVORITE -> userFacade.allowsFavoriteNotification(receiverUid);
            case TYPE_MENTION -> userFacade.allowsMentionNotification(receiverUid);
            default -> userFacade.allowsInteractionNotification(receiverUid);
        };
    }

    private Map<String, Object> reportReceiptContent(String sourceType, Long reportId,
                                                     String userStatus, String targetPath) {
        Map<String, Object> content = new LinkedHashMap<>();
        String normalizedStatus = normalizeReportUserStatus(userStatus);
        content.put("action", ACTION_REPORT_RECEIPT);
        content.put("sourceType", sourceType);
        content.put("reportId", reportId);
        content.put("userStatus", normalizedStatus);
        content.put("targetPath", targetPath);
        content.put("title", "你提交的举报已有处理结果。");
        content.put("message", REPORT_USER_STATUS_ACTION_TAKEN.equals(normalizedStatus)
                ? "平台已处理你举报的内容。"
                : "经复核，暂未发现明确违规。");
        content.put("message", reportReceiptMessage(normalizedStatus));
        content.put("dedupKey", ACTION_REPORT_RECEIPT + ":" + sourceType + ":" + reportId);
        return content;
    }

    private String normalizeReportUserStatus(String userStatus) {
        if (REPORT_USER_STATUS_ACTION_TAKEN.equals(userStatus)) {
            return REPORT_USER_STATUS_ACTION_TAKEN;
        }
        if (REPORT_USER_STATUS_CLOSED.equals(userStatus)) {
            return REPORT_USER_STATUS_CLOSED;
        }
        return REPORT_USER_STATUS_NOT_ACCEPTED;
    }

    private String reportReceiptMessage(String status) {
        if (REPORT_USER_STATUS_ACTION_TAKEN.equals(status)) {
            return "平台已处理你举报的内容。";
        }
        if (REPORT_USER_STATUS_CLOSED.equals(status)) {
            return "举报已关闭，平台已记录该反馈。";
        }
        return "经复核，暂未发现明确违规。";
    }

    private Map<String, Object> contactRequestContent(String action, Long requestId,
                                                      String targetPath, String message) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("action", action);
        content.put("requestId", requestId);
        content.put("targetPath", targetPath);
        content.put("message", message);
        content.put("dedupKey", action + ":" + requestId);
        return content;
    }

    private LambdaQueryWrapper<NotificationMessagePO> baseUnread(Long uid) {
        return new LambdaQueryWrapper<NotificationMessagePO>()
                .eq(NotificationMessagePO::getReceiverUid, uid)
                .eq(NotificationMessagePO::getIsRead, 0)
                .eq(NotificationMessagePO::getIsDeleted, 0);
    }

    private long countByType(Long uid, int type) {
        if (!messageTableReady()) {
            return 0L;
        }
        Long count = mapper.selectCount(baseUnread(uid).eq(NotificationMessagePO::getNotifType, type));
        return count == null ? 0L : count;
    }

    private boolean messageTableReady() {
        try {
            return mapper.tableExists() > 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean dedupKeyColumnReady() {
        try {
            return mapper.dedupKeyColumnExists() > 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private List<Long> normalizeReadIds(List<Long> notifIds) {
        if (notifIds == null || notifIds.isEmpty()) {
            return List.of();
        }
        return notifIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .limit(MAX_READ_BATCH_SIZE)
                .toList();
    }

    private List<Map<String, Object>> aggregateItems(List<NotificationMessagePO> rows, Map<Long, UserBriefDTO> senders) {
        List<Map<String, Object>> items = new ArrayList<>();
        List<NotificationMessagePO> group = new ArrayList<>();
        for (NotificationMessagePO row : rows) {
            if (group.isEmpty() || canAggregate(group.get(0), row)) {
                group.add(row);
                continue;
            }
            items.add(toGroupedItem(group, senders));
            group = new ArrayList<>();
            group.add(row);
        }
        if (!group.isEmpty()) {
            items.add(toGroupedItem(group, senders));
        }
        return items;
    }

    private boolean canAggregate(NotificationMessagePO head, NotificationMessagePO candidate) {
        if (head == null || candidate == null) {
            return false;
        }
        if (!isAggregatableType(head.getNotifType()) || !isAggregatableType(candidate.getNotifType())) {
            return false;
        }
        if (!java.util.Objects.equals(head.getNotifType(), candidate.getNotifType())) {
            return false;
        }
        if (!java.util.Objects.equals(head.getTargetType(), candidate.getTargetType())) {
            return false;
        }
        if (!java.util.Objects.equals(head.getTargetId(), candidate.getTargetId())) {
            return false;
        }
        if (head.getCreateTime() == null || candidate.getCreateTime() == null) {
            return false;
        }
        return Duration.between(candidate.getCreateTime(), head.getCreateTime()).toMinutes() <= AGGREGATION_WINDOW_MINUTES;
    }

    private boolean isAggregatableType(Integer notifType) {
        return notifType != null && (notifType == TYPE_LIKE || notifType == TYPE_FAVORITE);
    }

    private Map<String, Object> toGroupedItem(List<NotificationMessagePO> group, Map<Long, UserBriefDTO> senders) {
        NotificationMessagePO head = group.get(0);
        if (group.size() == 1) {
            return toItem(head, senders.get(head.getSenderUid()));
        }
        Map<String, Object> item = toItem(head, senders.get(head.getSenderUid()));
        int unreadCount = (int) group.stream()
                .filter(row -> row.getIsRead() == null || row.getIsRead() == 0)
                .count();
        Map<String, Object> content = new LinkedHashMap<>(parseContent(head.getContentJson()));
        content.put("aggregateCount", group.size());
        content.put("unreadCount", unreadCount);
        content.put("aggregated", true);
        item.put("content", content);
        item.put("notificationIds", group.stream().map(NotificationMessagePO::getId).toList());
        item.put("aggregateCount", group.size());
        item.put("unreadCount", unreadCount);
        item.put("isRead", unreadCount == 0);
        return item;
    }

    private Map<String, Object> toItem(NotificationMessagePO po, UserBriefDTO sender) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", po.getId());
        item.put("sender", sender);
        item.put("type", typeName(po.getNotifType()));
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

    private String normalizeDiscussionFollowQualityAction(String action) {
        if (action == null || action.isBlank()) {
            return null;
        }
        return switch (action) {
            case "discussion_follow_featured_reply",
                 "discussion_follow_author_pinned",
                 "discussion_follow_author_reply" -> action;
            default -> null;
        };
    }

    private String discussionFollowQualityMessage(String action) {
        return switch (action) {
            case "discussion_follow_featured_reply" -> "你关注的讨论有一条精选回复。";
            case "discussion_follow_author_pinned" -> "作者置顶了一条关键回应。";
            case "discussion_follow_author_reply" -> "作者补充了新的回应。";
            default -> "你关注的讨论有新的回应。";
        };
    }

    private String commentsTargetPath(Long postId) {
        return "/post/" + postId + "#comments";
    }
}
