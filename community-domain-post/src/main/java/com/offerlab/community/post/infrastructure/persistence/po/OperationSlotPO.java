package com.offerlab.community.post.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_operation_slot")
public class OperationSlotPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String slotCode;
    private String slotName;
    private String description;
    private String slotStatus;
    private Integer sortOrder;
    private Integer defaultLimit;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private String previewToken;
    private Integer currentVersion;
    private String publishedSnapshotJson;
    private String rollbackSnapshotJson;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
}
