package com.offerlab.community.interaction.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CommentReportDomainModeratorGuardTest {
    private static final Path ROOT = Path.of("").toAbsolutePath();

    private static String read(String relative) throws Exception {
        return Files.readString(ROOT.resolve(relative), StandardCharsets.UTF_8);
    }

    private static void assertContains(String source, String needle, String message) {
        assertTrue(source.contains(needle), message + " Missing: " + needle);
    }

    @Test
    void commentReportsUseDomainModeratorScopeAndDomainFilteredSql() throws Exception {
        String controller = read("src/main/java/com/offerlab/community/interaction/controller/InteractionController.java");
        String service = read("src/main/java/com/offerlab/community/interaction/application/CommentReportService.java");
        String mapper = read("src/main/java/com/offerlab/community/interaction/infrastructure/persistence/mapper/CommentReportMapper.java");

        assertContains(controller, "DomainModeratorService", "comment report controller must depend on domain moderator scope");
        assertContains(controller, "@RequestParam(required = false) Integer domain", "comment report list must accept domain");
        assertContains(controller, "domainModeratorService.requireModerateDomain", "comment report list must enforce domain scope");
        assertContains(controller, "reportService.listRecent(status, domain, limit, includeTestData)",
                "comment report list must pass domain to service");

        assertContains(service, "listRecent(Integer status, Integer domain", "comment report service must support domain filtering");
        assertContains(service, "domainModeratorService.requireModerateDomain(reviewerUid, post.getDomain())",
                "comment report review must enforce the reported post domain");
        assertContains(mapper, "LEFT JOIN t_post_extension e ON e.post_id = r.post_id",
                "comment report query must join post extension for domain");
        assertContains(mapper, "COALESCE(e.domain, 1) = #{domain}", "comment report query must filter by indexed post domain");
    }
}
