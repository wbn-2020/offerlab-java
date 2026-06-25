package com.offerlab.community.feed.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.feed.infrastructure.persistence.po.RecommendFeedNewCreatorSupportStatPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RecommendFeedNewCreatorSupportStatMapper extends BaseMapper<RecommendFeedNewCreatorSupportStatPO> {

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_feed_recommend_support_stat'
            """)
    int tableExists();
}
