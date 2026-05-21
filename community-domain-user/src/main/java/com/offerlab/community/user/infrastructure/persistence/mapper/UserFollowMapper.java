package com.offerlab.community.user.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.user.infrastructure.persistence.po.UserFollowPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserFollowMapper extends BaseMapper<UserFollowPO> {
}
