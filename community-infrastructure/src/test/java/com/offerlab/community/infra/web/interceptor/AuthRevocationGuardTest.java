package com.offerlab.community.infra.web.interceptor;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthRevocationGuardTest {

    @Test
    void degradedRevocationMustFailClosedForProtectedEndpoints() throws Exception {
        String interceptor = read("src/main/java/com/offerlab/community/infra/web/interceptor/AuthInterceptor.java");

        assertContains(interceptor, "if (uid != null && revocationCheckDegraded)");
        assertContains(interceptor, "if (!isPublic)");
        assertContains(interceptor, "throw new BizException(ErrorCode.UNAUTHORIZED)");
        assertContains(interceptor, "continue anonymously");
        assertContains(interceptor, "uid = null");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static void assertContains(String source, String expected) {
        assertTrue(source.contains(expected), "Expected source to contain: " + expected);
    }
}
