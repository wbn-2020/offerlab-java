package com.offerlab.community.user.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.user.infrastructure.persistence.po.UserPrivacySettingPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserPrivacySettingMapper extends BaseMapper<UserPrivacySettingPO> {
}
