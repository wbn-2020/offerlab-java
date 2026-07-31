package com.offerlab.community.interaction.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_int_user_revisit_item")
public class UserRevisitItemPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long uid;
    private String sourceType;
    private String sourceId;
    private String reasonType;
    private Long activityCursor;
    private String title;
    private String description;
    private String targetPath;
    private LocalDateTime dueAt;
    private String revisitStatus;
    private String dedupKey;
    private LocalDateTime completedAt;
    private LocalDateTime snoozedUntil;
    private LocalDateTime lastNotifiedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
