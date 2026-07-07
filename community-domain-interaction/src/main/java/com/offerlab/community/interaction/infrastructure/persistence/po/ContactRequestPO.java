package com.offerlab.community.interaction.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_int_contact_request")
public class ContactRequestPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long requesterUid;
    private Long receiverUid;
    private String sourceType;
    private Long sourceId;
    private String scene;
    private String messagePreview;
    private String requestStatus;
    private LocalDateTime receiverActionTime;
    private LocalDateTime expireTime;
    private Long reportId;
    private String dedupKey;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
