package com.offerlab.community.archtest;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoSeedTextQualityGuardTest {
    private static final List<String> KNOWN_MOJIBAKE_SAMPLES = List.of(
            "瀛楄妭璺冲姩",
            "闃块噷宸村反",
            "鑵捐",
            "缇庡洟",
            "灏忕孩涔",
            "鐧惧害",
            "娣辨祴绉戞妧",
            "鍚庣",
            "鍓嶇",
            "绠楁硶宸ョ▼甯",
            "婕旂ず绠＄悊鍛",
            "绋冲畾鎬ф不鐞",
            "娑堟伅闃熷垪",
            "浜岄潰",
            "鍥涙鍜岄噺鍖栫粨鏋");

    private static final List<String> REQUIRED_CHINESE_TEXT = List.of(
            "字节跳动",
            "Java 后端成长路线",
            "Kafka 稳定性治理",
            "OfferLab 演示管理员",
            "深测科技 Java 后端一面复盘：缓存、事务和慢 SQL",
            "Spring 事务在同类方法内部调用时为什么可能不生效？你在项目里怎么规避？",
            "同类方法调用不走代理，要主动说出事务边界为什么放在 service 编排层。",
            "数字生活应急包清单：备份、密码与双重验证");

    @Test
    void demoSeedMustNotContainKnownMojibake() throws Exception {
        String seed = readDemoSeed();

        for (String sample : KNOWN_MOJIBAKE_SAMPLES) {
            assertFalse(seed.contains(sample), () -> "demo seed contains mojibake sample: " + sample);
        }
        assertFalse(seed.contains("\uFFFD"), "demo seed must not contain Unicode replacement characters");
    }

    @Test
    void demoSeedMustKeepRepresentativeChineseContent() throws Exception {
        String seed = readDemoSeed();

        for (String requiredText : REQUIRED_CHINESE_TEXT) {
            assertTrue(seed.contains(requiredText),
                    () -> "demo seed is missing required Chinese text: " + requiredText);
        }
    }

    @Test
    void demoSeedMustNotDependOnDiceBearForAvatarRendering() throws Exception {
        String seed = readDemoSeed();
        String localAdminSeed = Files.readString(
                RepositoryTestPaths.resolve("db/local/seed_local_demo_admin.sql"),
                StandardCharsets.UTF_8);

        assertFalse(seed.contains("api.dicebear.com"),
                "demo seed avatars must not require the external DiceBear service");
        assertFalse(localAdminSeed.contains("api.dicebear.com"),
                "local admin seed avatars must not require the external DiceBear service");
    }

    @Test
    void existingLocalDatabaseRefreshMustReuseTheCanonicalSeedWithoutRewritingMigrationHistory() throws Exception {
        String refresh = Files.readString(
                RepositoryTestPaths.resolve("db/demo/refresh-existing-local-demo.sql"),
                StandardCharsets.UTF_8);
        String readme = Files.readString(RepositoryTestPaths.resolve("README.md"), StandardCharsets.UTF_8);
        String normalizedRefresh = refresh.toLowerCase(Locale.ROOT);

        assertTrue(refresh.contains("SOURCE db/init/99_seed.sql;"),
                "existing local demo refresh must reuse the current canonical seed");
        var directMutations = Pattern.compile(
                        "\\b(?:insert(?:\\s+ignore)?\\s+into|replace(?:\\s+into)?|update|delete\\s+from|call"
                                + "|truncate(?:\\s+table)?"
                                + "|drop(?:\\s+temporary)?\\s+table|alter\\s+table"
                                + "|create(?:\\s+temporary)?\\s+table)\\b[^;]*;",
                        Pattern.CASE_INSENSITIVE)
                .matcher(normalizedRefresh)
                .results()
                .map(result -> result.group())
                .toList();
        assertFalse(directMutations.isEmpty(),
                "refresh wrapper must retain its temporary assertion table");
        for (String statement : directMutations) {
            assertTrue(statement.contains("offerlab_demo_refresh_assertion"),
                    () -> "local demo refresh must not directly mutate business tables: " + statement);
        }
        assertTrue(readme.contains("SOURCE db/demo/refresh-existing-local-demo.sql;"),
                "README must direct existing local databases to the V22 refresh entry");
        assertTrue(readme.contains("--execute=\"SOURCE db/demo/refresh-existing-local-demo.sql;\""),
                "README must use the fail-fast mysql --execute entry point");
        assertTrue(readme.contains("--skip-force") && readme.contains("--skip-reconnect"),
                "README must override option-file force and reconnect settings");
        assertTrue(readme.contains("不要在交互式客户端中手工 `SOURCE`"),
                "README must reject interactive SOURCE because it can continue after an assertion error");
        assertTrue(readme.contains("不要") && readme.contains("使用 `--force`"),
                "README must reject mysql --force for transactional refreshes");
        assertTrue(readme.contains("MySQL 8.0.16+"),
                "README must declare the minimum version that enforces CHECK constraints");
        assertTrue(readme.contains("全新的专用 MySQL 会话"),
                "README must keep local admin DDL away from unrelated caller transactions");
        assertFalse(readme.contains("SOURCE db/migration/20260601_demo_question_seed_existing_db.sql;"),
                "README must not direct users back to immutable historical demo text");
    }

    private static String readDemoSeed() throws Exception {
        return Files.readString(
                RepositoryTestPaths.resolve("db/init/99_seed.sql"),
                StandardCharsets.UTF_8);
    }
}
