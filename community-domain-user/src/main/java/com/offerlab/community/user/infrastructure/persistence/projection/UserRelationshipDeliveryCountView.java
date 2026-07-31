package com.offerlab.community.user.infrastructure.persistence.projection;

import lombok.Data;

@Data
public class UserRelationshipDeliveryCountView {

    private String deliveryMode;
    private Long count;
}
