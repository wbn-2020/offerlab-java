package com.offerlab.community.analytics.api;

import java.util.List;

/**
 * Read-only V44 input for V45 consumers. V45 must not read V44 persistence directly.
 */
public interface ChannelQualityGovernancePlaybookInputQueryFacade {
    PlaybookInput getPublishedPlaybook(Long playbookVersionId);

    record PlaybookInput(
            String schemaVersion,
            Long playbookId,
            Long playbookVersionId,
            Integer versionNo,
            String contentSummary,
            String canonicalContentJson,
            String contentHash) {
        public static final String SCHEMA_VERSION = "V44_PLAYBOOK_INPUT_V1";
    }
}
