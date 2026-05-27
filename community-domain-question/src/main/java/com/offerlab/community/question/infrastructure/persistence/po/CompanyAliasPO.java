package com.offerlab.community.question.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_company_alias")
public class CompanyAliasPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String canonicalCompany;
    private String alias;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
