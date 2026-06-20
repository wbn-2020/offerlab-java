package com.offerlab.community.user.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UserSyntheticFilterGuardTest {

    @Test
    void publicUserSearchMustHideExplicitSyntheticAccounts() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/offerlab/community/user/application/UserApplicationService.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains(".filter(user -> !isSyntheticUser(user))"),
                "public user search must filter explicit synthetic accounts before returning recommendations");
        for (String marker : new String[]{"E2E", "SMOKE", "CODEX", "TESTDATA"}) {
            assertTrue(source.contains(marker), "synthetic user filter must recognize " + marker);
        }
    }
}
