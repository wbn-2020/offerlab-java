package com.offerlab.community.user.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UserTaskRateLimitGuardTest {

    @Test
    void userTaskCompletionWritesMustBeRateLimited() throws Exception {
        String controller = read("src/main/java/com/offerlab/community/user/controller/UserTaskController.java");

        assertContains(controller, "@PostMapping(\"/onboarding-tasks/{taskCode}/complete\")\n    @RateLimit");
        assertContains(controller, "@PostMapping(\"/daily-tasks/{taskCode}/complete\")\n    @RateLimit");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static void assertContains(String source, String expected) {
        assertTrue(source.contains(expected), "Expected source to contain: " + expected);
    }
}
