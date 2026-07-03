package com.offerlab.community.post.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SeoGuardTest {

    @Test
    void stageOneSeoOutputsMustExposePublicLinksAndSitemap() throws Exception {
        String dto = read("src/main/java/com/offerlab/community/post/api/dto/SeoLinkDTO.java");
        String service = read("src/main/java/com/offerlab/community/post/application/PublicSeoService.java");
        String controller = read("src/main/java/com/offerlab/community/post/controller/SeoController.java");
        String mapper = read("src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/PostMapper.java");

        assertContains(dto, "private String path");
        assertContains(dto, "private String url");
        assertContains(dto, "private String type");

        assertContains(service, "listPublicLinks");
        assertContains(service, "buildSitemapXml");
        assertContains(service, "MAX_PUBLIC_POST_LINKS");
        assertContains(service, "safeBuildSitemapXml");
        assertContains(service, "\"/\"");
        assertContains(service, "\"/explore\"");
        assertContains(service, "\"/about\"");
        assertContains(service, "\"/post/\"");
        assertContains(service, "escapeXml(link.getUrl())");
        assertContains(service, "stableSortPublicLinks");

        assertContains(controller, "@RequestMapping(\"/api/v1/seo\")");
        assertContains(controller, "@GetMapping(value = \"/sitemap.xml\"");
        assertContains(controller, "@GetMapping(\"/public-links\")");
        assertContains(controller, "MediaType.APPLICATION_XML_VALUE");

        assertContains(mapper, "selectPublicSeoPosts");
        assertContains(mapper, "p.visibility = 1");
        assertContains(mapper, "p.post_status = 1");
        assertContains(mapper, "p.is_deleted = 0");
        assertContains(mapper, "t_post_extension");
        assertContains(mapper, "NOT LIKE '%E2E%'");
        assertContains(mapper, "NOT LIKE '%SMOKE%'");
        assertContains(mapper, "NOT LIKE '%CODEX%'");
        assertContains(mapper, "NOT LIKE '%TESTDATA%'");
        assertContains(mapper, "NOT LIKE '%DEMO%'");
        assertContains(mapper, "NOT LIKE '%FIXTURE%'");
        assertContains(mapper, "ORDER BY p.id ASC");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static void assertContains(String source, String expected) {
        assertTrue(source.contains(expected), "Expected source to contain: " + expected);
    }
}
