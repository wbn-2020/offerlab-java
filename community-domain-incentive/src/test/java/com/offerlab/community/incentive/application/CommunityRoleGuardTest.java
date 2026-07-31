package com.offerlab.community.incentive.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommunityRoleGuardTest {

    @Test
    void roleEvidenceAndReviewContextKeepScopeAndRateLimitContracts() throws Exception {
        String me = read(
                "src/main/java/com/offerlab/community/incentive/controller/IncentiveMeController.java");
        String admin = read(
                "src/main/java/com/offerlab/community/incentive/controller/IncentiveAdminController.java");
        String service = read(
                "src/main/java/com/offerlab/community/incentive/application/CommunityRoleService.java");

        assertTrue(me.contains("@GetMapping(\"/roles/workspace\")"));
        assertTrue(me.contains("@GetMapping(\"/roles/{roleCode}/evidence\")"));
        assertTrue(me.contains("@RateLimit"));
        assertTrue(me.contains("UserContext.require()"));
        assertTrue(admin.contains("@GetMapping(\"/roles/review-context/{applicationId}\")"));
        assertTrue(admin.contains("@RateLimit"));
        assertTrue(admin.contains("reason"));
        assertTrue(service.contains("recordRequired"));
        assertTrue(service.contains("CommunityRoleAccessService"));
        assertTrue(service.contains("OPEN_MAINTENANCE_CANDIDATES"));

        String combined = (me + admin + service).toLowerCase();
        assertFalse(combined.contains("publiccontributionprofile"));
        assertFalse(combined.contains("automaticallygrantsauthority(true"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
