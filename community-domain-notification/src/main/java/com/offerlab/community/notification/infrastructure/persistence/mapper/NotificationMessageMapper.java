package com.offerlab.community.notification.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.notification.infrastructure.persistence.po.NotificationMessagePO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationMessageMapper extends BaseMapper<NotificationMessagePO> {
}
