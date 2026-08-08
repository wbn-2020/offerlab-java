package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.api.dto.ChannelQualityGovernanceAnalyticsRebuildCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityGovernanceAnalyticsRebuildResultDTO;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityGovernanceAnalyticsMapper;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityGovernanceAnalyticsRebuildRequestRow;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.security.AdminPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChannelQualityGovernanceAnalyticsRebuildService {

    private static final int V43_TABLE_COUNT = 11;
    private static final int MAX_RANGE_DAYS = 365;
    private static final int MAX_LIMIT = 10_000;
    private static final int DEFAULT_LIMIT = 5_000;
    private static final Set<String> METRIC_FAMILIES = Set.of(
            "CASE_FUNNEL", "HANDLING_DURATION", "OVERDUE", "REMINDER",
            "REWORK", "WITHDRAW", "RECURRENCE");

    private final ChannelQualityGovernanceAnalyticsMapper analyticsMapper;
    private final AdminPermissionService adminPermissionService;
    private final SnowflakeIdGenerator idGenerator;

    @Transactional
    public ChannelQualityGovernanceAnalyticsRebuildResultDTO request(
            ChannelQualityGovernanceAnalyticsRebuildCmd cmd,
            Long operatorUid) {
        requireOps(operatorUid);
        requireV43Tables();
        RebuildInput input = requireInput(cmd);
        String fingerprint = fingerprint(input);
        ChannelQualityGovernanceAnalyticsRebuildRequestRow existing =
                analyticsMapper.selectRebuildRequest(operatorUid, input.idempotencyKey());
        if (existing != null) {
            if (!fingerprint.equals(existing.getRequestFingerprint())) {
                throw new BizException(ErrorCode.PARAM_ERROR);
            }
            return toResult(existing, input, true);
        }
        if (!input.dryRun() && analyticsMapper.countActiveRebuildRequests(input.from(), input.to()) > 0) {
            throw new BizException(ErrorCode.CONCURRENT_MODIFICATION);
        }

        ChannelQualityGovernanceAnalyticsRebuildRequestRow row =
                new ChannelQualityGovernanceAnalyticsRebuildRequestRow();
        row.setId(idGenerator.nextId());
        row.setOperatorUid(operatorUid);
        row.setIdempotencyKey(input.idempotencyKey());
        row.setRequestFingerprint(fingerprint);
        row.setStatus(input.dryRun() ? "DRY_RUN_READY" : "REQUESTED");
        row.setDryRun(input.dryRun() ? 1 : 0);
        row.setScopeFrom(input.from());
        row.setScopeTo(input.to());
        row.setResultSummary(input.dryRun() ? "DRY_RUN_NOT_EXECUTED" : "REQUEST_STORED_NOT_STARTED");

        int inserted = analyticsMapper.insertRebuildRequestIgnore(row);
        if (inserted == 1) {
            return toResult(row, input, false);
        }
        ChannelQualityGovernanceAnalyticsRebuildRequestRow replay =
                analyticsMapper.selectRebuildRequest(operatorUid, input.idempotencyKey());
        if (replay == null) {
            throw new BizException(ErrorCode.DATABASE_ERROR);
        }
        if (!fingerprint.equals(replay.getRequestFingerprint())) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return toResult(replay, input, true);
    }

    private static RebuildInput requireInput(ChannelQualityGovernanceAnalyticsRebuildCmd cmd) {
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        LocalDateTime from = parseUtcDateBoundary(cmd.getFrom());
        LocalDateTime to = parseUtcDateBoundary(cmd.getTo());
        if (!to.isAfter(from) || Duration.between(from, to).toDays() > MAX_RANGE_DAYS) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        List<Integer> domains = normalizedDomains(cmd.getDomains());
        List<String> metricFamilies = normalizedMetricFamilies(cmd.getMetricFamilies());
        int limit = cmd.getLimit() == null ? DEFAULT_LIMIT : cmd.getLimit();
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String idempotencyKey = cmd.getIdempotencyKey() == null ? null : cmd.getIdempotencyKey().trim();
        if (!StringUtils.hasText(idempotencyKey) || idempotencyKey.length() > 96) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (StringUtils.hasText(cmd.getReason()) && cmd.getReason().trim().length() > 160) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return new RebuildInput(
                from,
                to,
                domains,
                metricFamilies,
                !Boolean.FALSE.equals(cmd.getDryRun()),
                limit,
                idempotencyKey);
    }

    private static List<Integer> normalizedDomains(List<Integer> rawDomains) {
        List<Integer> domains = rawDomains == null || rawDomains.isEmpty()
                ? new ArrayList<>(List.of(1, 2, 3, 4, 5))
                : new ArrayList<>(rawDomains);
        if (domains.size() > 5 || domains.stream().anyMatch(domain -> domain == null || domain < 1 || domain > 5)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return domains.stream().distinct().sorted().toList();
    }

    private static List<String> normalizedMetricFamilies(List<String> rawMetricFamilies) {
        List<String> metricFamilies = rawMetricFamilies == null || rawMetricFamilies.isEmpty()
                ? new ArrayList<>(METRIC_FAMILIES)
                : new ArrayList<>(rawMetricFamilies);
        if (metricFamilies.size() > METRIC_FAMILIES.size()) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        List<String> normalized = metricFamilies.stream()
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        if (normalized.isEmpty() || normalized.stream().anyMatch(value -> !METRIC_FAMILIES.contains(value))) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private static LocalDateTime parseUtcDateBoundary(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        try {
            Instant instant = OffsetDateTime.parse(value.trim()).toInstant();
            LocalDateTime utc = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
            if (!utc.toLocalTime().equals(LocalTime.MIDNIGHT)) {
                throw new BizException(ErrorCode.PARAM_ERROR);
            }
            return utc;
        } catch (DateTimeException ex) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private static String fingerprint(RebuildInput input) {
        String canonical = String.join("|",
                "V43_REBUILD_REQUEST_V1",
                input.from().toInstant(ZoneOffset.UTC).toString(),
                input.to().toInstant(ZoneOffset.UTC).toString(),
                String.join(",", input.domains().stream().map(String::valueOf).toList()),
                String.join(",", input.metricFamilies()),
                String.valueOf(input.dryRun()),
                String.valueOf(input.limit()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
    }

    private static ChannelQualityGovernanceAnalyticsRebuildResultDTO toResult(
            ChannelQualityGovernanceAnalyticsRebuildRequestRow row,
            RebuildInput input,
            boolean idempotentReplay) {
        return ChannelQualityGovernanceAnalyticsRebuildResultDTO.builder()
                .status(row.getStatus())
                .dryRun(row.getDryRun() != null && row.getDryRun() == 1)
                .scopeFrom(row.getScopeFrom().toInstant(ZoneOffset.UTC))
                .scopeTo(row.getScopeTo().toInstant(ZoneOffset.UTC))
                .domainCount(input.domains().size())
                .metricFamilyCount(input.metricFamilies().size())
                .limit(input.limit())
                .idempotentReplay(idempotentReplay)
                .backgroundWorkStarted(false)
                .build();
    }

    private void requireOps(Long operatorUid) {
        if (operatorUid == null || operatorUid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        adminPermissionService.requireScope(operatorUid, AdminPermissionService.ROLE_OPS);
    }

    private void requireV43Tables() {
        try {
            if (analyticsMapper.v43TablesExist() == V43_TABLE_COUNT) {
                return;
            }
        } catch (RuntimeException ignored) {
            // Rebuild requests are not accepted until their own persistence exists.
        }
        throw new BizException(ErrorCode.DEPENDENCY_ERROR);
    }

    private record RebuildInput(
            LocalDateTime from,
            LocalDateTime to,
            List<Integer> domains,
            List<String> metricFamilies,
            boolean dryRun,
            int limit,
            String idempotencyKey) {
    }
}
