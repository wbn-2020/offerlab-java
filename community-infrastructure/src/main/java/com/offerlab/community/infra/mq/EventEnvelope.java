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
    private String version = "v1";

    /** 重试次数 */
    private Integer retryCount = 0;

    /** 业务负载 */
    private T payload;
}
