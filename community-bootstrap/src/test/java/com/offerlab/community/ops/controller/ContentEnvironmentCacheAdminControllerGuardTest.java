package com.offerlab.community.ops.controller;

import com.offerlab.community.infra.web.ratelimit.RateLimit;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentEnvironmentCacheAdminControllerGuardTest {

    @Test
    void cacheInvalidationEndpointIsRateLimitedFailClosed() throws Exception {
        Method method = ContentEnvironmentCacheAdminController.class.getDeclaredMethod(
                "invalidate", ContentEnvironmentCacheAdminController.InvalidationRequest.class);
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);
        assertNotNull(rateLimit);
        assertEquals("'content-env:cache-invalidation:' + #uid", rateLimit.key());
        assertTrue(rateLimit.rate() > 0);
        assertTrue(rateLimit.per() > 0);
        assertFalse(rateLimit.failOpen());
    }
}
