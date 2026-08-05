package com.offerlab.community.post.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.incentive.api.quota.BenefitCodes;
import com.offerlab.community.incentive.api.quota.EntitlementCapabilityDTO;
import com.offerlab.community.incentive.api.quota.EntitlementQuotaFacade;
import com.offerlab.community.incentive.api.quota.EntitlementReservationCmd;
import com.offerlab.community.incentive.api.quota.EntitlementReservationDTO;
import com.offerlab.community.incentive.api.quota.EntitlementUsageDTO;
import com.offerlab.community.post.api.dto.ContentAssistCapabilityDTO;
import com.offerlab.community.post.api.dto.ContentAssistEnhancedCmd;
import com.offerlab.community.post.api.dto.ContentAssistEnhancedResultDTO;
import com.offerlab.community.post.api.dto.ContentAssistEnhancedRequestSummaryDTO;
import com.offerlab.community.post.api.dto.ContentAssistEnhancedStatusDTO;
import com.offerlab.community.post.api.dto.ContentAssistQualityScoreCmd;
import com.offerlab.community.post.api.dto.ContentAssistQualityScoreDTO;
import com.offerlab.community.post.api.dto.ContentAssistTagTopicSuggestionsCmd;
import com.offerlab.community.post.api.dto.ContentAssistTagTopicSuggestionsDTO;
import com.offerlab.community.post.api.dto.ContentAssistWritingCmd;
import com.offerlab.community.post.api.dto.ContentAssistWritingDTO;
import com.offerlab.community.post.infrastructure.persistence.mapper.ContentAssistEnhancedRequestMapper;
import com.offerlab.community.post.infrastructure.persistence.po.ContentAssistEnhancedRequestPO;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentAssistEnhancedService {
    private static final int MAX_TEXT_LENGTH = 80;
    private static final long MIN_EXECUTION_WINDOW_SECONDS = 30L;
    private static final long MAX_EXECUTION_WINDOW_SECONDS = 15 * 60L;

    private final ObjectMapper objectMapper;
    private final ContentAssistAiClient aiClient;
    private final ContentAssistService contentAssistService;
    private final EntitlementQuotaFacade entitlementQuotaFacade;
    private final ContentAssistEnhancedRequestMapper requestMapper;
    private final ContentAssistEnhancedFinalizer finalizer;
    private final SnowflakeIdGenerator idGenerator;

    @Value("${offerlab.ai.content-assist.enhanced-enabled:false}")
    private boolean enhancedEnabled;

    @Value("${offerlab.ai.content-assist.enhanced-request-timeout-seconds:120}")
    private long enhancedRequestTimeoutSeconds;

    @Value("${offerlab.ai.content-assist.enhanced-execution-grace-seconds:30}")
    private long executionGraceSeconds;

    public ContentAssistCapabilityDTO capability(Long uid) {
        requireUid(uid);
        EntitlementCapabilityDTO quota = entitlementQuotaFacade.capability(
                uid, BenefitCodes.AI_ASSIST_QUOTA, BenefitCodes.CONTENT_ASSIST_ENHANCED);
        if (!enhancedEnabled || !aiClient.enabled() || !aiClient.configured()) {
            return capability(false, quota.availableQuantity(), "AI_ASSIST_NOT_CONFIGURED", quota.targetPath());
        }
        return capability(quota.available(), quota.availableQuantity(), quota.unavailableReason(), quota.targetPath());
    }

    public ContentAssistEnhancedResultDTO enhance(Long uid, String idempotencyKey, ContentAssistEnhancedCmd cmd) {
        requireUid(uid);
        validate(cmd, idempotencyKey);
        String fingerprint = fingerprint(uid, cmd);
        ContentAssistEnhancedRequestPO existing = requestMapper.selectByRequest(
                uid, BenefitCodes.CONTENT_ASSIST_ENHANCED, idempotencyKey);
        if (existing != null) {
            return replay(existing, fingerprint);
        }
        rejectPrivateBoundary(cmd);
        ContentAssistCapabilityDTO capability = capability(uid);
        if (!Boolean.TRUE.equals(capability.getAvailable())) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                    capability.getUnavailableReason() == null ? "AI_ASSIST_QUOTA_INSUFFICIENT"
                            : capability.getUnavailableReason());
        }
        ContentAssistEnhancedRequestPO request = runningRequest(uid, idempotencyKey, fingerprint, cmd);
        try {
            requestMapper.insertRunning(request);
        } catch (DuplicateKeyException ex) {
            ContentAssistEnhancedRequestPO concurrent = requestMapper.selectByRequest(
                    uid, BenefitCodes.CONTENT_ASSIST_ENHANCED, idempotencyKey);
            return replay(concurrent, fingerprint);
        }
        EntitlementReservationDTO reservation;
        try {
            reservation = entitlementQuotaFacade.reserve(new EntitlementReservationCmd(
                    uid, BenefitCodes.AI_ASSIST_QUOTA, BenefitCodes.CONTENT_ASSIST_ENHANCED, 1L,
                    idempotencyKey, fingerprint, "CONTENT_ASSIST", String.valueOf(request.getId()),
                    "CONTENT_ASSIST_ENHANCED", executionWindowSeconds()));
            if (requestMapper.attachUsage(request.getId(), uid, reservation.usageId()) != 1) {
                entitlementQuotaFacade.release(new com.offerlab.community.incentive.api.quota.EntitlementReleaseCmd(
                        uid, reservation.usageId(), BenefitCodes.CONTENT_ASSIST_ENHANCED,
                        "AI_ASSIST_REQUEST_UNAVAILABLE"));
                throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "AI_ASSIST_REQUEST_UNAVAILABLE");
            }
        } catch (RuntimeException ex) {
            finalizer.failWithoutReservation(uid, request.getId(), errorCode(ex, "AI_ASSIST_QUOTA_INSUFFICIENT"));
            throw ex;
        }
        try {
            ContentAssistAiClient.Completion completion = aiClient.complete(ContentAssistScene.ENHANCED, promptOf(cmd));
            ContentAssistEnhancedResultDTO result = enhancedResult(uid, cmd, completion, fingerprint);
            ContentAssistEnhancedFinalizer.Finalization finalization = finalizer.finalizeSuccess(
                    uid, request.getId(), reservation.usageId(), BenefitCodes.CONTENT_ASSIST_ENHANCED, result,
                    completion.provider(), completion.promptTokens(), completion.completionTokens(),
                    completion.estimatedCostMicros(),
                    () -> rulesResult(uid, cmd, fingerprint, "AI_ASSIST_RESERVATION_EXPIRED"));
            return finalization.result();
        } catch (RuntimeException ex) {
            return fallback(uid, request, reservation, cmd, fingerprint, errorCode(ex, "AI_ASSIST_PROVIDER_FAILED"));
        } catch (Exception ex) {
            return fallback(uid, request, reservation, cmd, fingerprint, errorCode(ex, "AI_ASSIST_PROVIDER_FAILED"));
        }
    }

    public ContentAssistEnhancedStatusDTO status(Long uid, String idempotencyKey) {
        requireUid(uid);
        requireIdempotencyKey(idempotencyKey);
        ContentAssistEnhancedRequestPO request = requestMapper.selectByRequest(
                uid, BenefitCodes.CONTENT_ASSIST_ENHANCED, idempotencyKey);
        if (request == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return statusOf(uid, request);
    }

    public ContentAssistEnhancedStatusDTO statusByRequestId(Long uid, Long requestId) {
        requireUid(uid);
        if (requestId == null || requestId <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        ContentAssistEnhancedRequestPO request = requestMapper.selectByIdAndUid(
                requestId, uid, BenefitCodes.CONTENT_ASSIST_ENHANCED);
        if (request == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return statusOf(uid, request);
    }

    public List<ContentAssistEnhancedRequestSummaryDTO> recent(Long uid, int limit) {
        requireUid(uid);
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? 5 : limit, 10));
        return requestMapper.selectRecentByUser(uid, BenefitCodes.CONTENT_ASSIST_ENHANCED, safeLimit).stream()
                .map(request -> summaryOf(uid, request))
                .toList();
    }

    private ContentAssistEnhancedStatusDTO statusOf(Long uid, ContentAssistEnhancedRequestPO request) {
        ContentAssistEnhancedResultDTO result = resultOf(request);
        EntitlementUsageDTO usage = request.getUsageId() == null ? null : entitlementQuotaFacade.findByRequest(
                uid, BenefitCodes.CONTENT_ASSIST_ENHANCED, request.getIdempotencyKey());
        String usageStatus = usage == null ? null : usage.status().name();
        return ContentAssistEnhancedStatusDTO.builder()
                .requestStatus(request.getRequestStatus())
                .usageStatus(usageStatus)
                .quotaConsumed("SUCCEEDED".equals(request.getRequestStatus())
                        && usage != null
                        && usage.status() == com.offerlab.community.incentive.api.quota.EntitlementUsageStatus.CONFIRMED)
                .requestFingerprint(request.getRequestFingerprint())
                .errorCode(request.getErrorCode())
                .result(result)
                .build();
    }

    private ContentAssistEnhancedRequestSummaryDTO summaryOf(Long uid, ContentAssistEnhancedRequestPO request) {
        EntitlementUsageDTO usage = request.getUsageId() == null ? null : entitlementQuotaFacade.findByRequest(
                uid, BenefitCodes.CONTENT_ASSIST_ENHANCED, request.getIdempotencyKey());
        return ContentAssistEnhancedRequestSummaryDTO.builder()
                .requestId(request.getId())
                .requestStatus(request.getRequestStatus())
                .usageStatus(usage == null ? null : usage.status().name())
                .quotaConsumed("SUCCEEDED".equals(request.getRequestStatus())
                        && usage != null
                        && usage.status() == com.offerlab.community.incentive.api.quota.EntitlementUsageStatus.CONFIRMED)
                .requestFingerprint(request.getRequestFingerprint())
                .errorCode(request.getErrorCode())
                .createTime(request.getCreateTime())
                .updateTime(request.getUpdateTime())
                .build();
    }

    private ContentAssistEnhancedResultDTO fallback(Long uid, ContentAssistEnhancedRequestPO request,
                                                    EntitlementReservationDTO reservation,
                                                    ContentAssistEnhancedCmd cmd, String fingerprint,
                                                    String errorCode) {
        ContentAssistEnhancedResultDTO result = rulesResult(uid, cmd, fingerprint, errorCode);
        try {
            EntitlementUsageDTO usage = finalizer.releaseFallback(uid, request.getId(), reservation.usageId(),
                    BenefitCodes.CONTENT_ASSIST_ENHANCED, result, errorCode);
            result.setRequestStatus("FALLBACK");
            result.setUsageStatus(usage.status().name());
            result.setQuotaConsumed(false);
            return result;
        } catch (RuntimeException releaseError) {
            log.warn("content assist enhanced fallback finalization failed: requestId={}, error={}",
                    request.getId(), releaseError.getMessage());
            throw releaseError;
        }
    }

    private ContentAssistEnhancedResultDTO enhancedResult(Long uid, ContentAssistEnhancedCmd cmd,
                                                          ContentAssistAiClient.Completion completion,
                                                          String fingerprint) {
        JsonNode root = readObject(completion.contentJson());
        String summary = text(root, "summary", true);
        int score = score(root.path("qualityScore"));
        List<String> writingSuggestions = textList(root.path("writingSuggestions"), 6);
        List<String> qualitySuggestions = textList(root.path("qualitySuggestions"), 6);
        List<String> riskHints = textList(root.path("riskHints"), 4);
        if (summary == null || writingSuggestions.isEmpty() && qualitySuggestions.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "AI_ASSIST_INVALID_RESPONSE");
        }
        ContentAssistEnhancedResultDTO rules = rulesResult(uid, cmd, fingerprint, null);
        ContentAssistWritingDTO writing = rules.getWriting();
        writing.setProvider(completion.provider());
        writing.setFallbackUsed(false);
        writing.setSuggestedTitle(text(root, "suggestedTitle", false));
        writing.setSummary(summary);
        writing.setSuggestions(writingSuggestions);
        writing.setRiskHints(riskHints.isEmpty() ? writing.getRiskHints() : riskHints);
        writing.setPromptTokens(completion.promptTokens());
        writing.setCompletionTokens(completion.completionTokens());
        writing.setEstimatedCostMicros(completion.estimatedCostMicros());
        ContentAssistQualityScoreDTO quality = rules.getQuality();
        quality.setProvider(completion.provider());
        quality.setFallbackUsed(false);
        quality.setScore(score);
        quality.setSummary(text(root, "qualitySummary", false));
        quality.setSuggestions(qualitySuggestions);
        quality.setPromptTokens(completion.promptTokens());
        quality.setCompletionTokens(completion.completionTokens());
        quality.setEstimatedCostMicros(completion.estimatedCostMicros());
        return ContentAssistEnhancedResultDTO.builder()
                .requestStatus("SUCCEEDED")
                .usageStatus("CONFIRMED")
                .quotaConsumed(true)
                .replayed(false)
                .requestFingerprint(fingerprint)
                .provider(completion.provider())
                .writing(writing)
                .quality(quality)
                .tagTopic(rules.getTagTopic())
                .build();
    }

    private ContentAssistEnhancedResultDTO rulesResult(Long uid, ContentAssistEnhancedCmd cmd,
                                                       String fingerprint, String failureCode) {
        ContentAssistWritingDTO writing = contentAssistService.assistWriting(uid, ContentAssistWritingCmd.builder()
                .domain(cmd.getDomain()).postType(cmd.getPostType()).title(cmd.getTitle()).content(cmd.getContent())
                .tagNames(cmd.getTagNames()).assistContext(cmd.getAssistContext())
                .assistTemplateCode(cmd.getAssistTemplateCode()).build());
        ContentAssistQualityScoreDTO quality = contentAssistService.scoreQuality(uid, ContentAssistQualityScoreCmd.builder()
                .domain(cmd.getDomain()).postType(cmd.getPostType()).title(cmd.getTitle()).content(cmd.getContent())
                .tagNames(cmd.getTagNames()).assistContext(cmd.getAssistContext())
                .assistTemplateCode(cmd.getAssistTemplateCode()).build());
        ContentAssistTagTopicSuggestionsDTO tagTopic = contentAssistService.suggestTagsAndTopics(uid,
                ContentAssistTagTopicSuggestionsCmd.builder().domain(cmd.getDomain()).title(cmd.getTitle())
                        .content(cmd.getContent()).assistContext(cmd.getAssistContext())
                        .assistTemplateCode(cmd.getAssistTemplateCode()).build());
        return ContentAssistEnhancedResultDTO.builder()
                .requestStatus(failureCode == null ? "SUCCEEDED" : "FALLBACK")
                .usageStatus(failureCode == null ? "CONFIRMED" : "RELEASED")
                .quotaConsumed(failureCode == null)
                .replayed(false)
                .requestFingerprint(fingerprint)
                .provider(failureCode == null ? "rules" : "rules")
                .fallbackReason(failureCode)
                .writing(writing)
                .quality(quality)
                .tagTopic(tagTopic)
                .build();
    }

    private ContentAssistEnhancedResultDTO replay(ContentAssistEnhancedRequestPO request, String fingerprint) {
        if (request == null || !Objects.equals(request.getRequestFingerprint(), fingerprint)) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION.getCode(), "AI_ASSIST_IDEMPOTENCY_CONFLICT");
        }
        ContentAssistEnhancedResultDTO result = resultOf(request);
        if (result == null) {
            if (!"RUNNING".equals(request.getRequestStatus())) {
                throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "AI_ASSIST_RESULT_EXPIRED");
            }
            return ContentAssistEnhancedResultDTO.builder()
                    .requestStatus(request.getRequestStatus()).replayed(true)
                    .requestFingerprint(request.getRequestFingerprint()).build();
        }
        result.setReplayed(true);
        return result;
    }

    private ContentAssistEnhancedResultDTO resultOf(ContentAssistEnhancedRequestPO request) {
        if (request == null || request.getResultJson() == null || request.getResultJson().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(request.getResultJson(), ContentAssistEnhancedResultDTO.class);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "AI_ASSIST_RESULT_EXPIRED");
        }
    }

    private static ContentAssistCapabilityDTO capability(boolean available, long remaining, String reason, String targetPath) {
        return ContentAssistCapabilityDTO.builder()
                .available(available).remainingQuota(Math.max(0L, remaining)).unavailableReason(reason)
                .benefitCode(BenefitCodes.AI_ASSIST_QUOTA).consumerCode(BenefitCodes.CONTENT_ASSIST_ENHANCED)
                .targetPath(targetPath).build();
    }

    private ContentAssistEnhancedRequestPO runningRequest(Long uid, String idempotencyKey, String fingerprint,
                                                          ContentAssistEnhancedCmd cmd) {
        ContentAssistEnhancedRequestPO request = new ContentAssistEnhancedRequestPO();
        request.setId(idGenerator.nextId());
        request.setUid(uid);
        request.setConsumerCode(BenefitCodes.CONTENT_ASSIST_ENHANCED);
        request.setIdempotencyKey(idempotencyKey);
        request.setRequestFingerprint(fingerprint);
        request.setContentHash(ContentAssistSafety.sha256Hex(cmd.getContent()));
        request.setContentLength(cmd.getContent().length());
        return request;
    }

    private static ContentAssistPrompt promptOf(ContentAssistEnhancedCmd cmd) {
        return new ContentAssistPrompt(cmd.getDomain(), cmd.getPostType(), cmd.getTitle(), cmd.getContent(),
                cmd.getTagNames() == null ? List.of() : cmd.getTagNames(),
                cmd.getAssistContext() == null ? "" : cmd.getAssistContext().toString(), cmd.getAssistTemplateCode());
    }

    private JsonNode readObject(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("payload is not object");
            }
            return root;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "AI_ASSIST_INVALID_RESPONSE");
        }
    }

    private static String text(JsonNode node, String key, boolean required) {
        String value = node.path(key).asText("").trim();
        if (value.length() > MAX_TEXT_LENGTH) {
            value = value.substring(0, MAX_TEXT_LENGTH);
        }
        if (required && value.isBlank()) {
            return null;
        }
        return value.isBlank() ? null : value;
    }

    private static int score(JsonNode node) {
        if (!node.canConvertToInt()) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "AI_ASSIST_INVALID_RESPONSE");
        }
        return Math.max(0, Math.min(100, node.asInt()));
    }

    private static List<String> textList(JsonNode node, int max) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("").trim();
            if (value.isBlank()) {
                continue;
            }
            result.add(value.length() > MAX_TEXT_LENGTH ? value.substring(0, MAX_TEXT_LENGTH) : value);
            if (result.size() >= max) {
                break;
            }
        }
        return result;
    }

    private static String fingerprint(Long uid, ContentAssistEnhancedCmd cmd) {
        String source = uid + "|" + clean(cmd.getTitle()) + "|" + cmd.getPostType() + "|" + cmd.getDomain() + "|"
                + cmd.getContent() + "|" + String.join(",", cmd.getTagNames() == null ? List.of() : cmd.getTagNames())
                + "|" + clean(cmd.getAssistTemplateCode())
                + "|" + ContentAssistSafety.sha256Hex(String.valueOf(cmd.getAssistContext()));
        return ContentAssistSafety.sha256Hex(source);
    }

    private static void rejectPrivateBoundary(ContentAssistEnhancedCmd cmd) {
        String source = (clean(cmd.getTitle()) + "\n" + clean(cmd.getContent()) + "\n"
                + String.join("\n", cmd.getTagNames() == null ? List.of() : cmd.getTagNames())).toLowerCase(Locale.ROOT);
        int sensitive = List.of("简历", "jd", "投递", "模拟面试").stream()
                .mapToInt(value -> source.contains(value) ? 1 : 0).sum();
        if (sensitive >= 2 || sensitive >= 1 && (source.contains("私人") || source.contains("个人") || source.contains("训练"))) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "AI_ASSIST_PRIVATE_BOUNDARY");
        }
    }

    private static void validate(ContentAssistEnhancedCmd cmd, String idempotencyKey) {
        if (cmd == null || cmd.getContent() == null || cmd.getContent().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        requireIdempotencyKey(idempotencyKey);
    }

    private static void requireUid(Long uid) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private static void requireIdempotencyKey(String value) {
        if (value == null || value.isBlank() || value.length() > 96) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "Idempotency-Key is invalid");
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String errorCode(Exception ex, String fallback) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return fallback;
        }
        if (message.contains("TIMEOUT") || message.contains("timeout")) {
            return "AI_ASSIST_PROVIDER_TIMEOUT";
        }
        if (message.contains("AI_ASSIST_INVALID_RESPONSE")) {
            return "AI_ASSIST_INVALID_RESPONSE";
        }
        return fallback;
    }

    boolean recoverStaleRequest(ContentAssistEnhancedRequestPO request, long timeoutSeconds) {
        if (request == null || request.getId() == null || request.getUid() == null) {
            return false;
        }
        return finalizer.recoverTimedOutRequest(request.getUid(), request.getId(), request.getUsageId(),
                BenefitCodes.CONTENT_ASSIST_ENHANCED, timeoutSeconds);
    }

    int clearExpiredResults(int retentionHours, int limit) {
        return requestMapper.clearExpiredResults(Math.max(1, Math.min(retentionHours, 24 * 7)),
                Math.max(1, Math.min(limit, 1000)));
    }

    long effectiveRecoveryTimeoutSeconds(long configuredTimeoutSeconds) {
        return Math.min(MAX_EXECUTION_WINDOW_SECONDS,
                Math.max(Math.max(MIN_EXECUTION_WINDOW_SECONDS, configuredTimeoutSeconds), executionWindowSeconds()));
    }

    private long executionWindowSeconds() {
        long providerTimeoutMillis = Math.max(1L, aiClient.maximumCompletionDurationMillis());
        long providerTimeoutSeconds = (providerTimeoutMillis + 999L) / 1000L;
        long grace = Math.max(5L, Math.min(5 * 60L, executionGraceSeconds));
        return Math.min(MAX_EXECUTION_WINDOW_SECONDS, Math.max(MIN_EXECUTION_WINDOW_SECONDS,
                Math.max(enhancedRequestTimeoutSeconds, providerTimeoutSeconds + grace)));
    }
}
