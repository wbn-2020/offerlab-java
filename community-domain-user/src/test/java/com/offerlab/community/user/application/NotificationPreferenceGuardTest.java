package com.offerlab.community.user.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationPreferenceGuardTest {

    @Test
    void notificationPreferencesMustStayGranularAndBackwardCompatible() throws Exception {
        String migration = read("../db/migration/20260530_notification_preferences.sql");
        String init = read("../db/init/08_privacy.sql");
        String dto = read("src/main/java/com/offerlab/community/user/api/dto/UserPrivacySettingDTO.java");
        String po = read("src/main/java/com/offerlab/community/user/infrastructure/persistence/po/UserPrivacySettingPO.java");
        String facadeApi = read("src/main/java/com/offerlab/community/user/api/UserFacade.java");
        String facade = read("src/main/java/com/offerlab/community/user/application/UserFacadeImpl.java");
        String service = read("src/main/java/com/offerlab/community/user/application/UserApplicationService.java");
        String notification = read("../community-domain-notification/src/main/java/com/offerlab/community/notification/application/NotificationFacadeImpl.java");

        for (String column : new String[]{
                "like_notification",
                "comment_notification",
                "follow_notification",
                "favorite_notification",
                "mention_notification"
        }) {
            assertTrue(migration.contains(column), "migration must add " + column);
            assertTrue(init.contains(column), "fresh schema must include " + column);
        }
        assertTrue(migration.contains("ADD COLUMN like_notification TINYINT NOT NULL DEFAULT 1"), "new preference columns must default enabled");
        assertFalse(migration.toLowerCase().contains("drop table"), "preference migration must not drop tables");

        for (String field : new String[]{
                "likeNotification",
                "commentNotification",
                "followNotification",
                "favoriteNotification",
                "mentionNotification"
        }) {
            assertTrue(dto.contains("private Boolean " + field), "privacy DTO must expose " + field);
            assertTrue(po.contains("private Integer " + field), "privacy PO must persist " + field);
            assertTrue(service.contains("." + field + "(isEnabled(po.get" + upperFirst(field) + "()))"), "privacy DTO mapping must include " + field);
        }

        for (String method : new String[]{
                "allowsLikeNotification",
                "allowsCommentNotification",
                "allowsFollowNotification",
                "allowsFavoriteNotification",
                "allowsMentionNotification"
        }) {
            assertTrue(facadeApi.contains("boolean " + method + "(Long uid)"), "user facade must expose " + method);
            assertTrue(facade.contains("public boolean " + method + "(Long uid)"), "user facade impl must implement " + method);
        }

        assertTrue(facade.contains("return allowsInteraction(setting) && enabled(setting.getLikeNotification())"), "like notifications must respect the interaction master switch");
        assertTrue(facade.contains("return allowsInteraction(setting) && enabled(setting.getCommentNotification())"), "comment notifications must respect the interaction master switch");
        assertTrue(facade.contains("return allowsInteraction(setting) && enabled(setting.getFollowNotification())"), "follow notifications must respect the interaction master switch");
        assertTrue(facade.contains("return allowsInteraction(setting) && enabled(setting.getFavoriteNotification())"), "favorite notifications must respect the interaction master switch");
        assertTrue(facade.contains("return allowsInteraction(setting) && enabled(setting.getMentionNotification())"), "mention notifications must respect the interaction master switch");
        assertTrue(facade.contains("return enabled(setting.getInteractionNotification())"), "interaction master switch must remain authoritative");

        assertTrue(service.contains("toFlag(setting.getLikeNotification(), po.getLikeNotification())"), "old clients must not reset like preference when field is absent");
        assertTrue(service.contains("toFlag(setting.getCommentNotification(), po.getCommentNotification())"), "old clients must not reset comment preference when field is absent");
        assertTrue(service.contains("private static int toFlag(Boolean value, Integer fallback)"), "privacy update must preserve existing values for missing granular fields");

        assertTrue(notification.contains("allowsNotificationType"), "notification creation must route by concrete notification type");
        assertTrue(notification.contains("case TYPE_LIKE -> userFacade.allowsLikeNotification(receiverUid)"), "likes must use like preference");
        assertTrue(notification.contains("case TYPE_COMMENT -> userFacade.allowsCommentNotification(receiverUid)"), "comments must use comment preference");
        assertTrue(notification.contains("case TYPE_FOLLOWER -> userFacade.allowsFollowNotification(receiverUid)"), "follows must use follow preference");
        assertTrue(notification.contains("case TYPE_FAVORITE -> userFacade.allowsFavoriteNotification(receiverUid)"), "favorites must use favorite preference");
        assertTrue(notification.contains("case TYPE_MENTION -> userFacade.allowsMentionNotification(receiverUid)"), "mentions must use mention preference");
        assertTrue(notification.contains("userFacade.allowsSystemNotification(receiverUid)"), "system notifications must still honor system preference");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static String upperFirst(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
