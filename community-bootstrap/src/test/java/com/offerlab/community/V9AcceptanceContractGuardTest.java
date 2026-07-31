package com.offerlab.community;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class V9AcceptanceContractGuardTest {

    @Test
    void acceptanceEnvironmentAndHealthLayersStayAligned() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).getParent();
        String env = read(root.resolve(".env.acceptance.example"));
        String schema = read(root.resolve("scripts/check-schema-readiness.mjs"));
        String health = read(root.resolve(
                "community-bootstrap/src/main/java/com/offerlab/community/HealthController.java"));
        String runbook = read(root.resolve("docs/acceptance-runbook.md"));

        for (String name : new String[]{
                "DB_URL", "DB_USERNAME", "DB_PASSWORD",
                "OFFERLAB_DB_HOST", "OFFERLAB_DB_PORT", "OFFERLAB_DB_NAME",
                "OFFERLAB_DB_USER", "OFFERLAB_DB_PASSWORD"
        }) {
            assertTrue(env.contains(name + "="), "acceptance template must declare " + name);
        }
        assertTrue(schema.contains("parseJdbcUrl(process.env.DB_URL)"));
        assertTrue(schema.contains("process.env.DB_USERNAME"));
        assertTrue(schema.contains("process.env.DB_PASSWORD"));

        assertTrue(health.contains("\"/liveness\""));
        assertTrue(health.contains("\"/readiness\""));
        assertTrue(health.contains("\"/operator-health\""));
        assertTrue(health.contains("\"/readiness/strict\""));
        assertTrue(health.contains("AdminPermissionService.ROLE_OPS"));
        assertTrue(health.contains("releaseReady"));
        assertTrue(runbook.contains("Authentication alone is not sufficient"));
        assertTrue(runbook.contains("anonymous, member, content moderator"));
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
