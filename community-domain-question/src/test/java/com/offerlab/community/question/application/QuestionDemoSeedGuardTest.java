package com.offerlab.community.question.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionDemoSeedGuardTest {

    @Test
    void localSeedMustKeepQuestionAndPrepDemoData() throws Exception {
        String seed = readSeed();

        assertTrue(seed.contains("demo.admin@offerlab.local"), "seed must include a local demo/admin user");
        assertTrue(seed.contains("INSERT INTO t_post_main"), "seed must include public interview posts for question joins");
        assertTrue(seed.contains("INSERT INTO t_post_extension"), "seed must include company/position post metadata");
        assertTrue(seed.contains("INSERT INTO t_interview_question"), "seed must include interview questions");
        assertTrue(seed.contains("INSERT INTO t_interview_question_tag"), "seed must attach tags to questions");
        assertTrue(seed.contains("INSERT INTO t_user_prep_target"), "seed must include prep targets for /me/prep");
        assertTrue(seed.contains("INSERT INTO t_user_question_progress"), "seed must include user progress for /me/prep");
        assertTrue(seed.contains("answer_draft") && seed.contains("star_story"), "seed must include answer draft and STAR fields");
        assertTrue(seed.contains("INSERT INTO t_mock_interview_session"), "seed should include a demo mock interview summary");
        assertTrue(seed.contains("深测科技"), "seed must support /companies/深测科技/prep");

        assertTrue(count(seed, "SHA2\\('offerlab-demo-question-") >= 8,
                "seed must keep at least eight demo interview questions");
        assertTrue(count(seed, "ON DUPLICATE KEY UPDATE") >= 10,
                "demo seed inserts must remain idempotent");
    }

    @Test
    void existingDatabasePatchMustTargetRealRetestAdminUser() throws Exception {
        String migration = readMigration();

        assertTrue(migration.contains("db/migration"), "migration must document that it is for existing databases");
        assertTrue(migration.contains("SELECT id FROM t_user_account WHERE email = 'admin'"),
                "existing database patch must resolve the real retest admin uid");
        assertTrue(migration.contains("SET @demo_uid := COALESCE"),
                "existing database patch must keep a deterministic fallback uid");
        assertTrue(migration.contains("@demo_uid, 'company', '深测科技'"),
                "prep targets must bind to the resolved retest uid");
        assertTrue(migration.contains("@demo_uid, 990200000000000001"),
                "question progress must bind to the resolved retest uid");
        assertTrue(migration.contains("source_author_uid = VALUES(source_author_uid)"),
                "questions must refresh their source author uid for existing rows");
        assertTrue(migration.contains("missing_demo_seed_schema_columns"),
                "existing database patch must preflight required schema columns");
        assertTrue(migration.contains("run migrations through 20260601"),
                "existing database patch must explain how to fix missing schema columns");
        assertTrue(count(migration, "SHA2\\('offerlab-demo-question-") >= 8,
                "existing database patch must include at least eight demo questions");
        assertTrue(count(migration, "ON DUPLICATE KEY UPDATE") >= 10,
                "existing database patch must be idempotent");
    }

    @Test
    void demoDataVisibilityScriptMustCheckRealRetestSignals() throws Exception {
        String script = readVisibilityScript();

        assertTrue(script.contains("verify-demo-question-data"),
                "script name or output should make the check discoverable");
        assertTrue(script.contains("admin-email") && script.contains("'admin'"),
                "visibility script must default to the real retest admin account");
        assertTrue(script.contains("demo.admin@offerlab.local"),
                "visibility script must fall back to the fresh-init demo account");
        assertTrue(script.contains("retestEmails") && script.contains("retest_user"),
                "visibility script must report the actual retest account used for prep data");
        assertTrue(script.contains("visible_question_total"),
                "visibility script must verify public question visibility");
        assertTrue(script.contains("admin_prep_targets"),
                "visibility script must verify prep targets for the retest account");
        assertTrue(script.contains("admin_progress_rows"),
                "visibility script must verify progress rows for the retest account");
        assertTrue(script.contains("shence_visible_questions"),
                "visibility script must verify company prep high-frequency question data");
    }

    private static String readSeed() throws Exception {
        Path moduleRelative = Path.of("../db/init/99_seed.sql");
        Path rootRelative = Path.of("db/init/99_seed.sql");
        Path path = Files.exists(moduleRelative) ? moduleRelative : rootRelative;
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static String readMigration() throws Exception {
        Path moduleRelative = Path.of("../db/migration/20260601_demo_question_seed_existing_db.sql");
        Path rootRelative = Path.of("db/migration/20260601_demo_question_seed_existing_db.sql");
        Path path = Files.exists(moduleRelative) ? moduleRelative : rootRelative;
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static String readVisibilityScript() throws Exception {
        Path moduleRelative = Path.of("../scripts/verify-demo-question-data.mjs");
        Path rootRelative = Path.of("scripts/verify-demo-question-data.mjs");
        Path path = Files.exists(moduleRelative) ? moduleRelative : rootRelative;
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static long count(String text, String regex) {
        return Pattern.compile(regex).matcher(text).results().count();
    }
}
