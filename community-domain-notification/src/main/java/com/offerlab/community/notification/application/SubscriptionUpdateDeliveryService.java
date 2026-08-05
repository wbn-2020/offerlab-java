package com.offerlab.community.notification.application;

import com.offerlab.community.common.utils.LogMask;
import com.offerlab.community.notification.api.NotificationFacade;
import com.offerlab.community.user.api.UserFacade;
import com.offerlab.community.user.api.UserSubscriptionPreferenceFacade;
import com.offerlab.community.user.api.dto.UserSubscriptionPreferenceDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Routes FOLLOWER_UPDATE facts before a normal notification can be created.
 *
 * <p>A policy read failure is deliberately fail-closed. The caller may persist
 * an unresolved-policy retry, whose replay must enter this V25 policy path
 * again instead of choosing a delivery mode from task data.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionUpdateDeliveryService {

    private static final Set<String> SUPPORTED_SOURCE_TYPES = Set.of("TOPIC", "DISCUSSION", "NEED");

    private final UserSubscriptionPreferenceFacade subscriptionPreferenceFacade;
    private final UserFacade userFacade;
    private final NotificationFacade notificationFacade;
    private final SubscriptionUpdateDigestService digestService;

    @Value("${offerlab.subscription-delivery.enabled:true}")
    private boolean enabled;

    @Value("${offerlab.subscription-delivery.topic-enabled:true}")
    private boolean topicEnabled;

    @Value("${offerlab.subscription-delivery.discussion-enabled:true}")
    private boolean discussionEnabled;

    @Value("${offerlab.subscription-delivery.need-enabled:true}")
    private boolean needEnabled;

    /**
     * All commands in one invocation must share one subscription source. This
     * keeps preference lookup bounded and prevents an accidental receiver-first
     * aggregation from changing mixed-source routing semantics.
     */
    List<SubscriptionUpdateDeliveryResult> deliverForSource(
            Collection<SubscriptionUpdateDeliveryCommand> sourceCommands) {
        List<SubscriptionUpdateDeliveryCommand> commands = normalizeCommands(sourceCommands);
        if (commands.isEmpty()) {
            return List.of();
        }
        SubscriptionUpdateDeliveryCommand first = commands.get(0);
        if (!sameSource(commands, first.sourceType(), first.sourceId())) {
            throw new IllegalArgumentException("subscription update commands must share one source");
        }
        if (!isFeatureEnabled(first.sourceType())) {
            return resultsFor(commands, null,
                    SubscriptionUpdateDeliveryResult.Status.SUPPRESSED_FEATURE_DISABLED, null);
        }

        Map<Long, UserSubscriptionPreferenceDTO> preferences;
        try {
            preferences = subscriptionPreferenceFacade.findEffectiveForRecipients(
                    receiverUids(commands), first.sourceType(), first.sourceId());
        } catch (RuntimeException e) {
            log.warn("subscription delivery policy lookup failed closed: sourceType={} sourceId={} receivers={}",
                    first.sourceType(), LogMask.id(first.sourceId()), commands.size(), e);
            return resultsFor(commands, null,
                    SubscriptionUpdateDeliveryResult.Status.POLICY_UNAVAILABLE, e);
        }
        if (preferences == null) {
            IllegalStateException failure =
                    new IllegalStateException("subscription delivery policy lookup returned no result");
            log.warn("subscription delivery policy lookup returned no result: sourceType={} sourceId={} receivers={}",
                    first.sourceType(), LogMask.id(first.sourceId()), commands.size());
            return resultsFor(commands, null,
                    SubscriptionUpdateDeliveryResult.Status.POLICY_UNAVAILABLE, failure);
        }

        List<SubscriptionUpdateDeliveryResult> results = new ArrayList<>(commands.size());
        for (SubscriptionUpdateDeliveryCommand command : commands) {
            SubscriptionUpdateDeliveryMode mode =
                    SubscriptionUpdateDeliveryMode.fromPreference(preferences.get(command.receiverUid()));
            if (mode == null) {
                IllegalStateException failure =
                        new IllegalStateException("subscription delivery preference is invalid");
                log.warn("subscription delivery preference is invalid; delivery skipped: receiverUid={} sourceType={} sourceId={}",
                        LogMask.id(command.receiverUid()), command.sourceType(), LogMask.id(command.sourceId()));
                results.add(new SubscriptionUpdateDeliveryResult(
                        command, null, SubscriptionUpdateDeliveryResult.Status.POLICY_UNAVAILABLE, failure));
                continue;
            }
            results.add(deliverResolved(command, mode));
        }
        return List.copyOf(results);
    }

    /**
     * Replays an already-resolved route. The source preference is intentionally
     * not read again, while the current global notification switch remains a
     * hard opt-out boundary.
     */
    SubscriptionUpdateDeliveryResult deliverResolved(
            SubscriptionUpdateDeliveryCommand command, SubscriptionUpdateDeliveryMode mode) {
        validateCommand(command);
        if (mode == null) {
            throw new IllegalArgumentException("subscription delivery mode is required");
        }
        if (!isFeatureEnabled(command.sourceType())) {
            return new SubscriptionUpdateDeliveryResult(command, mode,
                    SubscriptionUpdateDeliveryResult.Status.SUPPRESSED_FEATURE_DISABLED, null);
        }
        if (mode == SubscriptionUpdateDeliveryMode.MUTED) {
            return new SubscriptionUpdateDeliveryResult(command, mode,
                    SubscriptionUpdateDeliveryResult.Status.SUPPRESSED_MUTED, null);
        }
        try {
            if (!isGloballyAllowed(command)) {
                return new SubscriptionUpdateDeliveryResult(command, mode,
                        SubscriptionUpdateDeliveryResult.Status.SUPPRESSED_GLOBAL, null);
            }
        } catch (RuntimeException e) {
            log.warn("subscription delivery global preference check failed closed: receiverUid={} sourceType={} sourceId={}",
                    LogMask.id(command.receiverUid()), command.sourceType(), LogMask.id(command.sourceId()), e);
            return new SubscriptionUpdateDeliveryResult(command, mode,
                    SubscriptionUpdateDeliveryResult.Status.POLICY_UNAVAILABLE, e);
        }

        try {
            if (mode == SubscriptionUpdateDeliveryMode.DIGEST) {
                SubscriptionUpdateDigestRecordResult recordResult = digestService.record(
                        new SubscriptionUpdateDigestCommand(
                                command.receiverUid(),
                                command.sourceType(),
                                command.sourceId(),
                                command.resourceType(),
                                command.resourceId(),
                                command.eventType(),
                                command.eventKey(),
                                command.actorUid(),
                                command.payloadOrEmpty(),
                                command.occurredAt()));
                if (recordResult == SubscriptionUpdateDigestRecordResult.TABLE_UNAVAILABLE) {
                    throw new IllegalStateException("subscription update digest table is unavailable");
                }
                return new SubscriptionUpdateDeliveryResult(command, mode,
                        SubscriptionUpdateDeliveryResult.Status.DELIVERED_DIGEST, null);
            }
            createImmediateNotification(command);
            return new SubscriptionUpdateDeliveryResult(command, mode,
                    SubscriptionUpdateDeliveryResult.Status.DELIVERED_IMMEDIATE, null);
        } catch (RuntimeException e) {
            return new SubscriptionUpdateDeliveryResult(command, mode,
                    SubscriptionUpdateDeliveryResult.Status.FAILED, e);
        }
    }

    private void createImmediateNotification(SubscriptionUpdateDeliveryCommand command) {
        switch (command.notificationKind()) {
            case SYSTEM -> notificationFacade.notifySystem(
                    command.receiverUid(),
                    command.notificationTargetType() == null ? null : command.notificationTargetType().longValue(),
                    command.notificationTargetId(),
                    command.payloadOrEmpty());
            case DISCUSSION_FOLLOW_COMMENT -> notificationFacade.notifyDiscussionFollowComment(
                    command.receiverUid(),
                    command.actorUid(),
                    command.resourceId(),
                    command.notificationTargetId());
            case DISCUSSION_FOLLOW_QUALITY_COMMENT -> notificationFacade.notifyDiscussionFollowQualityComment(
                    command.receiverUid(),
                    command.actorUid(),
                    command.resourceId(),
                    command.notificationTargetId(),
                    action(command.payloadOrEmpty()));
        }
    }

    private boolean isGloballyAllowed(SubscriptionUpdateDeliveryCommand command) {
        return switch (command.notificationKind()) {
            case SYSTEM -> userFacade.allowsSystemNotification(command.receiverUid());
            case DISCUSSION_FOLLOW_COMMENT, DISCUSSION_FOLLOW_QUALITY_COMMENT ->
                    userFacade.allowsCommentNotification(command.receiverUid());
        };
    }

    private boolean isFeatureEnabled(String sourceType) {
        if (!enabled) {
            return false;
        }
        return switch (normalizeSourceType(sourceType)) {
            case "TOPIC" -> topicEnabled;
            case "DISCUSSION" -> discussionEnabled;
            case "NEED" -> needEnabled;
            default -> false;
        };
    }

    private static List<SubscriptionUpdateDeliveryCommand> normalizeCommands(
            Collection<SubscriptionUpdateDeliveryCommand> sourceCommands) {
        if (sourceCommands == null || sourceCommands.isEmpty()) {
            return List.of();
        }
        List<SubscriptionUpdateDeliveryCommand> commands = new ArrayList<>();
        for (SubscriptionUpdateDeliveryCommand command : sourceCommands) {
            if (command == null) {
                continue;
            }
            validateCommand(command);
            commands.add(command);
        }
        return List.copyOf(commands);
    }

    private static List<SubscriptionUpdateDeliveryResult> resultsFor(
            List<SubscriptionUpdateDeliveryCommand> commands,
            SubscriptionUpdateDeliveryMode mode,
            SubscriptionUpdateDeliveryResult.Status status,
            RuntimeException failure) {
        return commands.stream()
                .map(command -> new SubscriptionUpdateDeliveryResult(command, mode, status, failure))
                .toList();
    }

    private static boolean sameSource(List<SubscriptionUpdateDeliveryCommand> commands,
                                      String sourceType,
                                      Long sourceId) {
        return commands.stream().allMatch(command ->
                normalizeSourceType(sourceType).equals(normalizeSourceType(command.sourceType()))
                        && sourceId.equals(command.sourceId()));
    }

    private static Collection<Long> receiverUids(List<SubscriptionUpdateDeliveryCommand> commands) {
        LinkedHashSet<Long> receiverUids = new LinkedHashSet<>();
        for (SubscriptionUpdateDeliveryCommand command : commands) {
            receiverUids.add(command.receiverUid());
        }
        return List.copyOf(receiverUids);
    }

    private static void validateCommand(SubscriptionUpdateDeliveryCommand command) {
        if (command == null
                || !isPositive(command.receiverUid())
                || !SUPPORTED_SOURCE_TYPES.contains(normalizeSourceType(command.sourceType()))
                || !isPositive(command.sourceId())
                || !StringUtils.hasText(command.resourceType())
                || !isPositive(command.resourceId())
                || !StringUtils.hasText(command.eventType())
                || !StringUtils.hasText(command.eventKey())
                || !isPositive(command.actorUid())
                || command.notificationKind() == null
                || !isPositive(command.notificationTargetId())
                || command.occurredAt() == null) {
            throw new IllegalArgumentException("subscription update delivery command is incomplete");
        }
        if (command.notificationKind() == SubscriptionUpdateNotificationKind.SYSTEM
                && command.notificationTargetType() != null
                && command.notificationTargetType() <= 0) {
            throw new IllegalArgumentException("subscription notification target type is invalid");
        }
    }

    private static String action(Map<String, Object> content) {
        Object value = content.get("action");
        if (!(value instanceof String action) || action.isBlank()) {
            throw new IllegalArgumentException("discussion quality action is required");
        }
        return action.trim();
    }

    private static String normalizeSourceType(String sourceType) {
        return sourceType == null ? "" : sourceType.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean isPositive(Long value) {
        return value != null && value > 0;
    }

    void setFeatureFlagsForTest(boolean enabled, boolean topicEnabled,
                                boolean discussionEnabled, boolean needEnabled) {
        this.enabled = enabled;
        this.topicEnabled = topicEnabled;
        this.discussionEnabled = discussionEnabled;
        this.needEnabled = needEnabled;
    }
}
