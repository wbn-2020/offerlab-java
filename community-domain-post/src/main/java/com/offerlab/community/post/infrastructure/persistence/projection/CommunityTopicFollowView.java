package com.offerlab.community.post.infrastructure.persistence.projection;

import com.offerlab.community.post.infrastructure.persistence.po.CommunityTopicPO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CommunityTopicFollowView extends CommunityTopicPO {
    private Long relationId;
}
