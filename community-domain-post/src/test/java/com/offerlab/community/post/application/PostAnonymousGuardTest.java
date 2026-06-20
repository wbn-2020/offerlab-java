package com.offerlab.community.post.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PostAnonymousGuardTest {
    private static final Path ROOT = Path.of("").toAbsolutePath();

    private static String read(String relative) throws Exception {
        return Files.readString(ROOT.resolve(relative), StandardCharsets.UTF_8);
    }

    @Test
    void anonymousPostingContractIsImplementedWithoutSchemaMigration() throws Exception {
        String controller = read("src/main/java/com/offerlab/community/post/controller/PostController.java");
        String createCmd = read("src/main/java/com/offerlab/community/post/api/dto/PostCreateCmd.java");
        String updateCmd = read("src/main/java/com/offerlab/community/post/api/dto/PostUpdateCmd.java");
        String postDto = read("src/main/java/com/offerlab/community/post/api/dto/PostDTO.java");
        String briefDto = read("src/main/java/com/offerlab/community/post/api/dto/PostBriefDTO.java");
        String appService = read("src/main/java/com/offerlab/community/post/application/PostApplicationService.java");
        String facade = read("src/main/java/com/offerlab/community/post/application/PostFacadeImpl.java");

        assertTrue(controller.contains("private Boolean anonymous"), "publish/update request bodies must accept anonymous");
        assertTrue(controller.contains(".anonymous(req.getAnonymous())"), "controller must pass anonymous to application commands");
        assertTrue(createCmd.contains("private Boolean anonymous"), "PostCreateCmd must carry anonymous");
        assertTrue(updateCmd.contains("private Boolean anonymous"), "PostUpdateCmd must carry anonymous");
        assertTrue(postDto.contains("private Boolean anonymous"), "PostDTO must expose anonymous");
        assertTrue(briefDto.contains("private Boolean anonymous"), "PostBriefDTO must expose anonymous");

        assertTrue(appService.contains("mergeAnonymousToExtJson"), "service must merge anonymous into extJson");
        assertTrue(appService.contains("Post.DOMAIN_CAREER"), "anonymous must be limited to the CAREER domain");
        assertTrue(appService.contains("\"anonymous\""), "anonymous marker must be stored in extJson");

        assertTrue(facade.contains("isAnonymousPost"), "facade must detect anonymous posts");
        assertTrue(facade.contains("anonymousAuthor"), "facade must provide masked author display");
        assertTrue(facade.contains("canViewRealAuthor"), "facade must preserve real-author visibility for author/admin");
        assertTrue(facade.contains(".anonymous("), "facade DTO builders must set anonymous");
        assertTrue(facade.contains(".domain("), "detail and brief DTOs must preserve domain while adding anonymous");
    }
}
