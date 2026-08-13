package com.offerlab.community.analytics.api;

/**
 * V42-owned command boundary for downstream controlled automation.
 *
 * <p>Consumers must not access V42 tables or notification policy directly.</p>
 */
public interface ChannelQualityGovernanceAutomationCommandFacade {

    AutomationCommandResult queueReminderIfEligible(
            Long todoId,
            Integer expectedTodoVersion,
            String reminderOccurrenceKey,
            String targetIdempotencyKey);

    AutomationCommandResult escalateTodoIfEligible(
            Long todoId,
            Integer expectedTodoVersion,
            String expectedEscalationLevel,
            String targetEscalationLevel,
            String targetIdempotencyKey);

    record AutomationCommandResult(
            String contractVersion,
            String outcome,
            Long todoId,
            Integer observedTodoVersion,
            String observedEscalationLevel,
            Long intentId,
            Long eventId,
            boolean replayed) {
    }
}
