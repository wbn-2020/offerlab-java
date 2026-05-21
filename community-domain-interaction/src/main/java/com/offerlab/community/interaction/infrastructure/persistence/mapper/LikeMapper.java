package com.offerlab.community.interaction.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.LikePO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LikeMapper extends BaseMapper<LikePO> {
}
