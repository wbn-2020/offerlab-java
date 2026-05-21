package com.offerlab.community.infra.mq.outbox;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 事务消息表 PO
 * 对应 t_outbox_message 表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_outbox_message")
public class OutboxMessage {
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 聚合根类型，如 post、user、interaction */
    private String aggregateType;

    /** 聚合根 ID */
    private Long aggregateId;

    /** 目标 Topic */
    private String topic;

    /** 消息体 JSON */
    private String payload;

    /** 消息状态：0 待发、1 已发、2 失败 */
    private Integer msgStatus;

    /** 重试次数 */
    private Integer retryCount;

    /** 下次重试时间 */
    private LocalDateTime nextRetryTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
