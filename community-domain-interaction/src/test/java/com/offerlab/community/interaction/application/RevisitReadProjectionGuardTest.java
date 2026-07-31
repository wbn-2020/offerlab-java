package com.offerlab.community.interaction.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RevisitReadProjectionGuardTest {

    @Test
    void digestRevisitProjectionIsReadOnlyAndRevalidatesPublicPosts() throws Exception {
        String facade = read("src/main/java/com/offerlab/community/interaction/api/RevisitReadFacade.java");
        String service = read("src/main/java/com/offerlab/community/interaction/application/UserRevisitService.java");
        String mapper = read("src/main/java/com/offerlab/community/interaction/infrastructure/persistence/mapper/UserRevisitItemMapper.java");

        assertTrue(facade.contains("Read-only projection"));
        assertTrue(service.contains("findVisibleStates"));
        assertTrue(service.contains("listVisibleByResourceKeys"));
        assertFalse(service.substring(service.indexOf("findVisibleStates"), service.indexOf("public PageResult"))
                .contains("refresh(uid)"));

        assertTrue(mapper.contains("r.uid = #{uid}"));
        assertTrue(mapper.contains("p.is_deleted = 0"));
        assertTrue(mapper.contains("p.post_status = 1"));
        assertTrue(mapper.contains("p.visibility = 1"));
        assertTrue(mapper.contains("CONCAT('POST:', p.id)"));
        assertTrue(mapper.contains("CASE"));
        assertTrue(service.contains("!result.containsKey(postKey)"));
        assertTrue(service.contains("toReadState(row, nativeKey)"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
