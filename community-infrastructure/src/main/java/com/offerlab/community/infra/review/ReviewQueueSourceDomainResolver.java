package com.offerlab.community.infra.review;

public interface ReviewQueueSourceDomainResolver {
    boolean supports(String sourceType);

    Integer resolveDomain(String sourceType, Long sourceId);
}
