package com.offerlab.community.infra.web.interceptor;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthRevocationGuardTest {

    @Test
    void degradedRevocationMustFailClosedForPersonalDataReads() throws Exception {
        String interceptor = read("src/main/java/com/offerlab/community/infra/web/interceptor/AuthInterceptor.java");

        assertContains(interceptor, "\"/api/v1/users/me\"");
        assertContains(interceptor, "\"/api/v1/notifications\"");
        assertContains(interceptor, "\"/api/v1/contact-requests\"");
        assertContains(interceptor, "\"/api/v1/comments/reports\"");
        assertContains(interceptor, "\"/api/v1/posts/reports\"");
        assertContains(interceptor, "\"/api/v1/post-drafts\"");
        assertContains(interceptor, "\"/api/v1/mock-interviews\"");
        assertContains(interceptor, "STRICT_REVOCATION_PREFIXES");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static void assertContains(String source, String expected) {
        assertTrue(source.contains(expected), "Expected source to contain: " + expected);
    }
}
