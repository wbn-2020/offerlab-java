package com.offerlab.community.archtest;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoSeedIdentityGuardTest {

    @Test
    void canonicalSeedMustProtectTheDedicatedDemoIdentityBeforeWritingData() throws Exception {
        String seed = read("db/init/99_seed.sql");

        assertTrue(seed.contains("SET @offerlab_demo_user_uid := 990000000000000001;"),
                "personalized demo data must stay on the dedicated identity");
        assertOrdered(seed,
                "SET @offerlab_demo_author_identity_conflicts",
                "INSERT INTO t_tag",
                "reserved account conflicts must fail before the first seed write");
        assertOrdered(seed,
                "SET @offerlab_demo_seed_asset_identity_conflicts",
                "INSERT INTO t_tag",
                "all deterministic demo asset conflicts must fail before the first seed write");
        assertOrdered(seed,
                "CREATE TEMPORARY TABLE offerlab_demo_seed_assertion",
                "INSERT INTO t_tag",
                "the canonical seed must create its fail-closed assertion table before persistent writes");
        assertOrdered(seed,
                "VALUES ('demo_asset_identity', @offerlab_demo_seed_asset_identity_conflicts)",
                "INSERT INTO t_tag",
                "the deterministic asset guard must execute before persistent seed writes");
        assertOrdered(seed,
                "SET SESSION autocommit = 0;",
                "INSERT INTO t_tag",
                "fresh-init seed writes must begin with autocommit disabled");
        assertOrdered(seed,
                "-- END GENERATED FROM db/migration/20260712_demo_community_seed.sql",
                "SET SESSION autocommit = @offerlab_demo_seed_original_autocommit;",
                "canonical seed must restore autocommit only after every persistent write succeeds");
        assertTrue(seed.contains("CONSTRAINT chk_offerlab_demo_seed_no_conflicts CHECK (conflict_count = 0)"),
                "deterministic asset conflicts must fail through a MySQL-enforced CHECK constraint");
        assertFalse(seed.contains("PREPARE "),
                "canonical seed guards must not rely on statements that MySQL cannot prepare");
        assertFalse(seed.contains("SIGNAL SQLSTATE ''45000''"),
                "canonical seed guards must not hide SIGNAL inside dynamic SQL");
        assertTrue(seed.contains("account.email = 'demo.admin@offerlab.local'"),
                "only the explicit local demo admin identity may be preserved");
        assertTrue(seed.contains("email = IF(@offerlab_preserve_existing_local_admin = 1, email, VALUES(email))"),
                "an enabled local demo admin email must not be overwritten");
        assertTrue(seed.contains("password_hash = IF(@offerlab_preserve_existing_local_admin = 1, password_hash, VALUES(password_hash))"),
                "an enabled local demo admin password must not be overwritten");
        assertTrue(seed.contains("account_status = IF(@offerlab_preserve_existing_local_admin = 1, account_status, VALUES(account_status))"),
                "an enabled local demo admin must not be disabled by refresh");
        assertTrue(seed.contains("(990300000000000001, @offerlab_demo_user_uid"),
                "prep targets must use the dedicated demo identity");
        assertTrue(seed.contains("(990400000000000001, @offerlab_demo_user_uid"),
                "mock sessions must use the dedicated demo identity");
        assertTrue(count(seed, "uid = VALUES\\(uid\\)") >= 4,
                "all personalized demo upserts must repair deterministic uid ownership");
        assertFalse(seed.contains("INSERT INTO t_user_admin"),
                "the canonical seed must not grant administrator permissions");
        assertTrue(seed.contains("SET @community_demo_uid := @offerlab_demo_user_uid;"),
                "community demo content must remain on the reserved demo identity");
        assertFalse(seed.contains("SELECT id FROM t_user_account WHERE email = 'admin'"),
                "canonical seed must not attach community demo content to a guessed real admin");
        assertFalse(seed.contains("SELECT id FROM t_user_account WHERE is_deleted = 0 ORDER BY id"),
                "canonical seed must not attach community demo content to the first active account");
    }

    @Test
    void existingDatabaseRefreshMustFailClosedWithoutGuessingARealUser() throws Exception {
        String refresh = read("db/demo/refresh-existing-local-demo.sql");

        assertTrue(refresh.contains("SET @offerlab_demo_user_uid := 990000000000000001;"),
                "existing refresh must use the dedicated demo identity");
        assertFalse(refresh.contains("WHERE email = 'admin'"),
                "existing refresh must not attach private demo data to a guessed admin account");
        assertFalse(refresh.contains("ORDER BY id"),
                "existing refresh must not attach private demo data to the first active account");
        assertOrdered(refresh,
                "SET @offerlab_demo_refresh_identity_conflicts",
                "SOURCE db/init/99_seed.sql;",
                "all deterministic row conflicts must be checked before the canonical seed runs");
        assertOrdered(refresh,
                "CREATE TEMPORARY TABLE offerlab_demo_refresh_assertion",
                "START TRANSACTION;",
                "existing refresh must create its fail-closed assertion before starting writes");
        assertOrdered(refresh,
                "VALUES ('personalized_identity_preflight', @offerlab_demo_refresh_identity_conflicts)",
                "SOURCE db/init/99_seed.sql;",
                "existing refresh must enforce identity preflight before canonical writes");
        assertTrue(count(refresh, "NOT \\(uid <=> @offerlab_demo_user_uid\\)") >= 15,
                "every deterministic personalized row must reject ownership reassignment before refresh");
        assertOrdered(refresh,
                "SET SESSION autocommit = 0;",
                "START TRANSACTION;",
                "existing refresh must disable autocommit before opening its transaction");
        assertOrdered(refresh,
                "START TRANSACTION;",
                "SOURCE db/init/99_seed.sql;",
                "existing refresh must start a transaction before canonical writes");
        assertOrdered(refresh,
                "SET @offerlab_demo_seed_asset_identity_conflicts := NULL;",
                "SOURCE db/init/99_seed.sql;",
                "existing refresh must clear stale guard state before sourcing the canonical seed");
        assertOrdered(refresh,
                "SOURCE db/init/99_seed.sql;",
                "SET @offerlab_demo_refresh_postcondition_failures",
                "existing refresh must verify canonical writes before commit");
        assertTrue(refresh.contains("COALESCE(@offerlab_demo_seed_asset_identity_conflicts, 1) = 0"),
                "existing refresh must reject stale or failed canonical asset guards");
        assertTrue(refresh.contains("COALESCE(@offerlab_demo_refresh_identity_conflicts, 1) = 0"),
                "existing refresh must include personalized ownership preflight in final postconditions");
        assertOrdered(refresh,
                "SET @offerlab_demo_refresh_postcondition_failures",
                "VALUES ('refresh_postconditions', @offerlab_demo_refresh_postcondition_failures)",
                "existing refresh must calculate postconditions before enforcing them");
        assertOrdered(refresh,
                "VALUES ('refresh_postconditions', @offerlab_demo_refresh_postcondition_failures)",
                "COMMIT;",
                "existing refresh must commit only after its CHECK assertion succeeds");
        assertOrdered(refresh,
                "COMMIT;",
                "SET SESSION autocommit = @offerlab_demo_refresh_original_autocommit;",
                "existing refresh must restore the caller's autocommit mode only after commit");
        assertFalse(refresh.contains("PREPARE "),
                "existing refresh must not prepare unsupported transaction or SIGNAL statements");
        assertFalse(refresh.contains("'COMMIT'") || refresh.contains("'ROLLBACK'"),
                "existing refresh must not hide transaction control inside dynamic SQL");
        assertTrue(refresh.contains("demo_retest_account_status"),
                "refresh output must expose whether the dedicated demo identity is login-ready");
    }

    @Test
    void localAdminSeedMustRemainExplicitAndRejectIdentityCollisions() throws Exception {
        String localAdmin = read("db/local/seed_local_demo_admin.sql");
        String gitignore = read(".gitignore");

        assertTrue(gitignore.lines()
                        .map(String::trim)
                        .anyMatch("!db/local/seed_local_demo_admin.sql"::equals),
                "the reviewed local admin seed must remain tracked while other local SQL stays ignored");
        assertTrue(localAdmin.contains("SET @enable_local_demo_admin_seed := COALESCE"),
                "callers must be able to opt in without editing the tracked seed");
        assertTrue(localAdmin.contains("LENGTH(TRIM(@local_demo_admin_bcrypt_hash)) <> 60"),
                "local admin seed must require a complete bcrypt hash");
        assertTrue(localAdmin.contains("('$2a$', '$2b$', '$2y$')"),
                "local admin seed must restrict bcrypt prefixes");
        assertTrue(localAdmin.contains("REGEXP '^(0[4-9]|[12][0-9]|3[01])$'"),
                "local admin seed must reject unsupported bcrypt costs");
        assertTrue(localAdmin.contains("REGEXP '^[./A-Za-z0-9]{53}$'"),
                "local admin seed must reject malformed bcrypt payloads");
        assertOrdered(localAdmin,
                "START TRANSACTION;",
                "SET identity_conflicts = (",
                "identity collision checks must run inside the protected transaction");
        assertOrdered(localAdmin,
                "SET identity_conflicts = (",
                "INSERT INTO t_user_account",
                "identity collisions must fail before credentials are written");
        assertTrue(localAdmin.contains("SET @local_demo_admin_uid := 990000000000000001;"),
                "local admin promotion must stay on the reserved demo identity");
        assertOrdered(localAdmin,
                "START TRANSACTION;",
                "INSERT INTO t_user_account",
                "local admin writes must run in one transaction");
        assertTrue(localAdmin.contains("SET postcondition_failures = IF("),
                "local admin seed must verify account, profile, and role before commit");
        assertOrdered(localAdmin,
                "SET postcondition_failures = IF(",
                "COMMIT;",
                "local admin postconditions must pass before commit");
        assertTrue(localAdmin.contains("DECLARE EXIT HANDLER FOR SQLEXCEPTION")
                        && localAdmin.contains("ROLLBACK;"),
                "local admin seed must roll back incomplete writes");
        assertTrue(localAdmin.contains("SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;"),
                "local admin identity preflight must protect reserved uid and email ranges");
        assertTrue(localAdmin.contains("MySQL 8.0.16 or newer"),
                "local admin seed must reject servers that do not enforce CHECK constraints");
        assertOrdered(localAdmin,
                "MySQL 8.0.16 or newer",
                "CREATE TEMPORARY TABLE offerlab_local_admin_assertion",
                "the version guard must run before creating a CHECK-based assertion table");
        assertOrdered(localAdmin,
                "COMMIT;",
                "SET @local_demo_admin_seed_succeeded := 1;",
                "local admin success must be recorded only after commit succeeds");
        assertOrdered(localAdmin,
                "CALL offerlab_seed_local_demo_admin()",
                "SET @local_demo_admin_bcrypt_hash := NULL",
                "local admin seed must clear credentials immediately after the transactional call");
        assertOrderedAfter(localAdmin,
                "SET @local_demo_admin_bcrypt_hash := NULL",
                "DROP PROCEDURE IF EXISTS offerlab_seed_local_demo_admin",
                "local admin seed must clear the bcrypt hash even if routine cleanup later fails");
        assertOrdered(localAdmin,
                "SET @local_demo_admin_bcrypt_hash := NULL",
                "INSERT INTO offerlab_local_admin_assertion",
                "local admin seed must clear the bcrypt hash before reporting a handled failure");
        assertTrue(localAdmin.contains("CONSTRAINT chk_offerlab_local_admin_seed_succeeded CHECK (failure_count = 0)"),
                "handled local admin failures must be rethrown through a MySQL-enforced CHECK constraint");
        assertFalse(localAdmin.contains("PREPARE "),
                "local admin failure reporting must not rely on dynamic SIGNAL statements");
        assertFalse(localAdmin.contains("api.dicebear.com"),
                "local admin avatar rendering must not require an external service");
    }

    @Test
    void verificationMustPreferTheDeterministicPrepOwner() throws Exception {
        String verifier = read("scripts/verify-demo-question-data.mjs");

        assertTrue(verifier.contains("seed_owner"),
                "verification must discover the account that owns deterministic prep data");
        assertTrue(verifier.contains("target.id = 990300000000000001"),
                "verification must resolve the canonical prep target owner");
        assertTrue(verifier.contains("requested_user"),
                "an explicit --admin-email request must still override auto-discovery");
        assertTrue(count(verifier, "WHERE '\\$\\{escapeSql\\(config\\.adminEmail\\)\\}' = ''") >= 2,
                "an explicit --admin-email request must disable seed-owner and fallback account discovery");
        assertTrue(verifier.contains("u.account_status = 1"),
                "verification must not claim a disabled demo account is login-ready");
        assertTrue(verifier.contains("--defaults-extra-file="),
                "verification must keep database credentials out of process arguments");
        assertFalse(verifier.contains("--password="),
                "verification must not expose database credentials in process arguments");
        assertTrue(verifier.contains("args.has('password')"),
                "verification must explicitly reject the deprecated command-line password option");
        assertOrdered(verifier,
                "try {",
                "mysqlDefaultsDirectory = mkdtempSync",
                "temporary credential directory creation must be inside the cleanup boundary");
        assertOrdered(verifier,
                "mysqlDefaultsDirectory = mkdtempSync",
                "writeFileSync(mysqlDefaultsFile",
                "credential file writes must be covered by the cleanup boundary");
    }

    private static String read(String path) throws Exception {
        return Files.readString(RepositoryTestPaths.resolve(path), StandardCharsets.UTF_8);
    }

    private static long count(String text, String regex) {
        return Pattern.compile(regex).matcher(text).results().count();
    }

    private static void assertOrdered(String text, String before, String after, String message) {
        int beforeIndex = text.indexOf(before);
        int afterIndex = text.indexOf(after);
        assertTrue(beforeIndex >= 0, () -> message + " (missing before marker: " + before + ")");
        assertTrue(afterIndex >= 0, () -> message + " (missing after marker: " + after + ")");
        assertTrue(beforeIndex < afterIndex, message);
    }

    private static void assertOrderedAfter(String text, String before, String after, String message) {
        int beforeIndex = text.indexOf(before);
        int afterIndex = beforeIndex < 0 ? -1 : text.indexOf(after, beforeIndex + before.length());
        assertTrue(beforeIndex >= 0, () -> message + " (missing before marker: " + before + ")");
        assertTrue(afterIndex >= 0, () -> message + " (missing later marker: " + after + ")");
    }
}
