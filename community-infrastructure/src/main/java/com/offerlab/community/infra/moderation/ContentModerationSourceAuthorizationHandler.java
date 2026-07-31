package com.offerlab.community.infra.moderation;

public interface ContentModerationSourceAuthorizationHandler {

    boolean supports(String scope, String sourceType);

    void requireAuthorized(Long uid, Long sourceId);
}
