package com.offerlab.community.analytics.api;

/**
 * Read-only V44 case binding input for V45 consumers.
 */
public interface ChannelQualityRiskCasePlaybookInputQueryFacade {
    CasePlaybookInput getCasePlaybook(Long casePlaybookId);

    record CasePlaybookInput(
            String schemaVersion,
            Long casePlaybookId,
            Long caseId,
            Long playbookId,
            Long playbookVersionId,
            String bindingStatus,
            String sourceContentHash,
            String snapshotContentJson,
            String snapshotHash,
            String applicabilityResult,
            String completionEvaluationHash) {
        public static final String SCHEMA_VERSION = "V44_CASE_PLAYBOOK_INPUT_V1";
    }
}
