package com.offerlab.community.incentive.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardGuardDayBoundaryTest {

    @Test
    void dailyTotalRollsOnTheDatabaseDayNotTheJvmDay() {
        LocalDate dbToday = LocalDate.of(2026, 7, 26);

        // Same DB day → daily total counts, regardless of the JVM clock.
        assertEquals(30, AccountLedgerService.dailyAwardedFor(guard(dbToday, dbToday, 30)));

        // Counter from the previous DB day → daily total resets.
        assertEquals(0, AccountLedgerService.dailyAwardedFor(guard(dbToday.minusDays(1), dbToday, 30)));

        // DB day ahead of the counter (timezone skew scenario) → still resets.
        assertEquals(0, AccountLedgerService.dailyAwardedFor(guard(dbToday, dbToday.plusDays(1), 30)));
    }

    @Test
    void sqlDateAndLocalDateProjectionsAreBothAccepted() {
        LocalDate day = LocalDate.of(2026, 7, 26);
        Map<String, Object> guard = new HashMap<>();
        guard.put("counterDate", Date.valueOf(day));
        guard.put("dbToday", Date.valueOf(day));
        guard.put("dailyAwarded", 12L);
        assertEquals(12, AccountLedgerService.dailyAwardedFor(guard));
    }

    @Test
    void missingDbDayFallbackDoesNotDependOnAClockEqualityAtMidnight() {
        Map<String, Object> guard = new HashMap<>();
        guard.put("counterDate", LocalDate.MIN);
        guard.put("dailyAwarded", 7L);
        assertEquals(0, AccountLedgerService.dailyAwardedFor(guard));

        guard.remove("counterDate");
        assertEquals(0, AccountLedgerService.dailyAwardedFor(guard));
    }

    @Test
    void lockProjectionReportsTheDatabaseDay() throws Exception {
        String mapper = Files.readString(
                Path.of("src/main/java/com/offerlab/community/incentive/infrastructure/IncentiveMapper.java"),
                StandardCharsets.UTF_8);
        int lockStart = mapper.indexOf("Map<String, Object> lockRewardGuard(");
        assertTrue(lockStart >= 0, "lockRewardGuard must exist");
        String selectBlock = mapper.substring(Math.max(0, lockStart - 600), lockStart);
        assertTrue(selectBlock.contains("CURRENT_DATE AS dbToday"),
                "the guard lock projection must report the DB current day for read-side comparison");

        String service = Files.readString(
                Path.of("src/main/java/com/offerlab/community/incentive/application/AccountLedgerService.java"),
                StandardCharsets.UTF_8);
        assertFalse(service.contains("LocalDate.now().equals(counterDate)"),
                "reward cap reads must not compare the DB counter day against the JVM clock");
    }

    private static Map<String, Object> guard(LocalDate counterDate, LocalDate dbToday, long dailyAwarded) {
        Map<String, Object> guard = new HashMap<>();
        guard.put("counterDate", counterDate);
        guard.put("dbToday", dbToday);
        guard.put("dailyAwarded", dailyAwarded);
        return guard;
    }
}
