package com.offerlab.community.infra.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * 统一事件消息体外层包装
 * 便于通用处理、幂等消费、链路追踪
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventEnvelope<T> {
    /** 消息唯一标识，用于幂等消费 */
    private String messageId;

    /** 事件类型，如 POST_PUBLISHED、LIKE 等 */
    private String eventType;

    /** 消息发送时间戳（毫秒） */
    private Long timestamp;

    /** 链路追踪 ID */
    private String traceId;

    /** 消息版本，便于后续升级 */
    @Builder.Default
    private String version = "v1";

    /** 结构契约版本；与历史 version 并存，旧消息反序列化时允许为空。 */
    @Builder.Default
    private String schemaVersion = "1";

    /** 事件来源资源类型，例如 post、collaboration_need。 */
    private String sourceType;

    /** 事件来源资源 ID。 */
    private String sourceId;

    /** 触发事件的用户；系统事件允许为空。 */
    private Long actorUid;

    /** 业务事实发生时间；旧事件仍可回退到 timestamp。 */
    private Long occurredAt;

    /** 可见性边界，例如 PUBLIC、USER、OPS。 */
    @Builder.Default
    private String visibilityScope = "INTERNAL";

    /** 消费幂等键；默认与 messageId 相同。 */
    private String idempotencyKey;

    /** 重试次数 */
    @Builder.Default
    private Integer retryCount = 0;

    /** 业务负载 */
    private T payload;
}
