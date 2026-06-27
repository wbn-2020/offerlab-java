package com.offerlab.community.post.application;

import com.offerlab.community.post.controller.KnowledgeController;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KnowledgeRelationPublicContractTest {

    @Test
    void publicKnowledgeRelationsContractDoesNotExposeSeriesSeed() {
        Method controllerMethod = Arrays.stream(KnowledgeController.class.getDeclaredMethods())
                .filter(method -> "relations".equals(method.getName()))
                .findFirst()
                .orElseThrow();
        Method serviceMethod = Arrays.stream(KnowledgeRelationService.class.getDeclaredMethods())
                .filter(method -> "explore".equals(method.getName()))
                .findFirst()
                .orElseThrow();

        assertEquals(5, controllerMethod.getParameterCount(),
                "public knowledge controller should only accept postId/tagId/topicId/domain/limit");
        assertEquals(5, serviceMethod.getParameterCount(),
                "knowledge relation service should align with the public seed contract");
    }
}
