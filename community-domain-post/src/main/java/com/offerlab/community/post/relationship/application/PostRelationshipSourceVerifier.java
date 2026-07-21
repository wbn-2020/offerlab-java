package com.offerlab.community.post.relationship.application;

import com.offerlab.community.post.relationship.infrastructure.persistence.RelationshipMapper;
import com.offerlab.community.user.api.UserRelationshipSourceVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class PostRelationshipSourceVerifier implements UserRelationshipSourceVerifier {

    private static final Set<String> SUPPORTED_SOURCE_TYPES =
            Set.of("TOPIC", "DISCUSSION", "NEED", "SERIES");

    private final RelationshipMapper relationshipMapper;

    @Override
    public boolean supports(String sourceType) {
        return SUPPORTED_SOURCE_TYPES.contains(sourceType);
    }

    @Override
    public boolean exists(Long uid, String sourceType, Long sourceId) {
        return supports(sourceType)
                && relationshipMapper.existsPostRelationship(uid, sourceType, sourceId) > 0;
    }
}
