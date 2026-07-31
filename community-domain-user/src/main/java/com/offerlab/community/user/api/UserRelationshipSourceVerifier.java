package com.offerlab.community.user.api;

/**
 * Verifies that a user owns an active relationship with a typed source.
 *
 * <p>The user domain owns preference persistence, while each source domain
 * remains responsible for deciding whether its relationship is active.</p>
 */
public interface UserRelationshipSourceVerifier {

    boolean supports(String sourceType);

    boolean exists(Long uid, String sourceType, Long sourceId);
}
