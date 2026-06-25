package com.offerlab.community.post.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PostControllerVisibilityGuardTest {

    @Test
    void publicDetailControllerMustPassViewerContextBeforeCountingView() throws Exception {
        String controller = read("src/main/java/com/offerlab/community/post/controller/PostController.java");

        assertTrue(controller.contains("Long viewerUid = UserContext.get();"),
                "public detail controller must read optional viewer uid");
        assertTrue(controller.contains("PostDTO p = postFacade.getPost(postId, viewerUid);"),
                "public detail controller must pass viewer uid into facade visibility check");
        assertTrue(controller.contains("postService.incrView(postId);"),
                "public detail controller must increment view after visible detail is resolved");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
