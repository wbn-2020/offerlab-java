package com.offerlab.community.post.application;

import com.offerlab.community.post.api.dto.PostCreateCmd;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostDomainPublishingContractTest {

    @Test
    void createCommandDeclaresDomainAsRequired() throws Exception {
        NotNull annotation = PostCreateCmd.class.getDeclaredField("domain").getAnnotation(NotNull.class);

        assertNotNull(annotation);
        assertEquals("请选择频道", annotation.message());
    }

    @Test
    void publishingDocsAndParentDescriptionMatchCommunityPositioning() throws Exception {
        String controller = read("src/main/java/com/offerlab/community/post/controller/PostController.java");
        String parentPom = read("../pom.xml");

        assertFalse(controller.contains("为空时服务端默认 TECH"));
        assertTrue(controller.contains("发布时必须显式选择有效频道"));
        assertTrue(parentPom.contains(
                "<description>Comprehensive Content Community Platform - Parent POM</description>"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
