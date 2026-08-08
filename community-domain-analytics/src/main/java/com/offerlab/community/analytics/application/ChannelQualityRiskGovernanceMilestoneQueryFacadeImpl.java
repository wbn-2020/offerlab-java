package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityRiskGovernanceMilestoneFactRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityRiskGovernanceMilestoneQueryMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChannelQualityRiskGovernanceMilestoneQueryFacadeImpl
        implements ChannelQualityRiskGovernanceMilestoneQueryFacade {

    private static final String CONTRACT_VERSION = "V41_FACT_V1";
    private static final Set<String> FACT_TYPES = Set.of(
            "OWNER_ASSIGNED", "OWNER_ACKNOWLEDGED", "PLAN_RECORDED",
            "CLOSE_SNAPSHOT_GENERATED", "RETROSPECTIVE_PENDING",
            "RETROSPECTIVE_OWNER_ASSIGNED", "RETROSPECTIVE_COMPLETED");

    private final ChannelQualityRiskGovernanceMilestoneQueryMapper milestoneQueryMapper;

    @Override
    @Transactional(readOnly = true)
    public List<V41GovernanceFact> listCaseFacts(Long caseId) {
        if (caseId == null || caseId <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        List<ChannelQualityRiskGovernanceMilestoneFactRow> rows;
        try {
            rows = milestoneQueryMapper.listFactsByCaseId(caseId);
        } catch (RuntimeException ex) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        if (rows == null) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        long previousId = 0;
        for (ChannelQualityRiskGovernanceMilestoneFactRow row : rows) {
            validate(row, caseId, previousId);
            previousId = row.getSourceFactId();
        }
        return rows.stream().map(row -> new V41GovernanceFact(
                row.getSourceFactId(), row.getCaseId(), row.getDomain(), row.getCaseVersion(),
                row.getFactType(), row.getOccurredAt().atZone(ZoneOffset.UTC).toInstant(),
                row.getOwnerUid(), row.getResponsibilityScope(), row.getResponsibilityEpoch(),
                row.getCaseStatusAfter(), row.getRequiredAction(), row.getRetrospectiveId(),
                row.getContractVersion())).toList();
    }

    private static void validate(ChannelQualityRiskGovernanceMilestoneFactRow row, Long caseId, long previousId) {
        if (row == null || row.getSourceFactId() == null || row.getSourceFactId() <= previousId
                || !caseId.equals(row.getCaseId()) || row.getDomain() == null
                || row.getDomain() < 1 || row.getDomain() > 5 || row.getCaseVersion() == null
                || row.getCaseVersion() < 0 || !FACT_TYPES.contains(row.getFactType())
                || row.getOccurredAt() == null || !CONTRACT_VERSION.equals(row.getContractVersion())) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        boolean responsibilityFact = Set.of(
                "OWNER_ASSIGNED", "OWNER_ACKNOWLEDGED", "PLAN_RECORDED",
                "RETROSPECTIVE_PENDING", "RETROSPECTIVE_OWNER_ASSIGNED",
                "RETROSPECTIVE_COMPLETED").contains(row.getFactType());
        if (responsibilityFact && (row.getOwnerUid() == null || row.getOwnerUid() <= 0
                || row.getResponsibilityEpoch() == null || row.getResponsibilityEpoch() < 1)) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        if ("OWNER_ASSIGNED".equals(row.getFactType())
                && !validOwnerAssignedAction(row.getCaseStatusAfter(), row.getRequiredAction())) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
    }

    private static boolean validOwnerAssignedAction(String caseStatusAfter, String requiredAction) {
        return ("OPEN".equals(caseStatusAfter) && "ACKNOWLEDGE_CASE".equals(requiredAction))
                || ("ACKNOWLEDGED".equals(caseStatusAfter) && "RECORD_PLAN".equals(requiredAction))
                || (Set.of("IN_PROGRESS", "RESOLVED", "CLOSED").contains(caseStatusAfter)
                && "NONE".equals(requiredAction));
    }
}
