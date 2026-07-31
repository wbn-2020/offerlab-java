package com.offerlab.community.infra.web.config;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebMvcConfigHealthProbeGuardTest {

    @Test
    void readinessSummaryIsAnonymousButStrictDetailsRemainProtected() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/offerlab/community/infra/web/config/WebMvcConfig.java"),
                StandardCharsets.UTF_8
        );

        assertTrue(source.contains("\"/api/v1/health/readiness\""),
                "deployment readiness probes must not require a bearer token");
        assertFalse(source.contains("\"/api/v1/health/readiness/strict\""),
                "strict readiness details must continue through AuthInterceptor");
        assertFalse(source.contains("\"/api/v1/health/readiness/**\""),
                "readiness exclusions must not accidentally expose future detail endpoints");
    }
}
