package com.offerlab.community.user.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserLoggingGuardTest {

    @Test
    void registrationLogsMustNotWriteRawEmail() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/offerlab/community/user/application/UserApplicationService.java"), StandardCharsets.UTF_8);

        assertTrue(source.contains("maskEmail(email)"), "registration log must mask user email");
        assertTrue(source.contains("private String maskEmail"), "email masking helper must stay local and explicit");
        assertFalse(source.contains("log.info(\"user registered: uid={} email={}\", uid, email)"), "registration log must not include raw email");
    }
}