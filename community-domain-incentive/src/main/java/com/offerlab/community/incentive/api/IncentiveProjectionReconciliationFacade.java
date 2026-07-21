package com.offerlab.community.incentive.api;

import com.offerlab.community.incentive.api.IncentiveDtos.ReconciliationDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.RewardInboxReconcileDTO;

public interface IncentiveProjectionReconciliationFacade {

    int REWARD_INBOX_SLA_MINUTES = 60;

    ReconciliationDTO previewAccountProjection(Integer limit, String reason, Long operatorUid);

    ReconciliationDTO reconcileAccountProjection(Integer limit, String reason, Long operatorUid);

    int previewExpiredRoleGrants(Integer limit, Long operatorUid);

    int reconcileExpiredRoleGrants(Integer limit, String reason, Long operatorUid);

    int previewOverdueRewardInbox(Integer limit, Long operatorUid);

    RewardInboxReconcileDTO reconcileOverdueRewardInbox(
            Integer limit, String reason, Long operatorUid);
}
