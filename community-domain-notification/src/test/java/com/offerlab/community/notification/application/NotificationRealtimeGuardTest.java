package com.offerlab.community.notification.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationRealtimeGuardTest {

    @Test
    void notificationRealtimeStatusMustExposePollingFriendlyState() throws Exception {
        String dto = read("src/main/java/com/offerlab/community/notification/api/dto/NotificationRealtimeStatusDTO.java");
        String facadeApi = read("src/main/java/com/offerlab/community/notification/api/NotificationFacade.java");
        String facade = read("src/main/java/com/offerlab/community/notification/application/NotificationFacadeImpl.java");
        String controller = read("src/main/java/com/offerlab/community/notification/controller/NotificationController.java");
        String mapper = read("src/main/java/com/offerlab/community/notification/infrastructure/persistence/mapper/NotificationMessageMapper.java");

        assertTrue(dto.contains("Map<String, Long> unread"), "status DTO must expose typed unread counters");
        assertTrue(dto.contains("latestUnreadId"), "status DTO must expose the latest unread id for change detection");
        assertTrue(dto.contains("latestUnreadAt"), "status DTO must expose the latest unread timestamp");
        assertTrue(dto.contains("pollIntervalSeconds"), "status DTO must tell the frontend how often to poll");

        assertTrue(facadeApi.contains("NotificationRealtimeStatusDTO getRealtimeStatus(Long uid)"), "facade must expose realtime status");
        assertTrue(controller.contains("@GetMapping(\"/realtime-status\")"), "controller must expose realtime status endpoint");
        assertTrue(controller.contains("UserContext.require()"), "realtime status endpoint must require the current user");
        assertTrue(facade.contains("getUnreadCountByType(uid)"), "status must reuse canonical unread counters");
        assertTrue(facade.contains("mapper.selectLatestUnread(uid)"), "status must include latest unread change marker");
        assertTrue(facade.contains("REALTIME_POLL_INTERVAL_SECONDS"), "status must return a controlled polling interval");
        assertTrue(mapper.contains("selectLatestUnread"), "mapper must fetch the latest unread notification");
        assertTrue(mapper.contains("AND is_read = 0"), "latest marker must only inspect unread notifications");
        assertTrue(mapper.contains("AND is_deleted = 0"), "latest marker must ignore deleted notifications");
        assertTrue(mapper.contains("ORDER BY create_time DESC, id DESC"), "latest marker must be deterministic");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
