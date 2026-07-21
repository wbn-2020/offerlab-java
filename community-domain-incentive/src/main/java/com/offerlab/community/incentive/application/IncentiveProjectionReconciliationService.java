package com.offerlab.community.incentive.application;

import com.offerlab.community.incentive.api.IncentiveDtos.ReconciliationDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.RewardInboxReconcileDTO;
import com.offerlab.community.incentive.api.IncentiveProjectionReconciliationFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IncentiveProjectionReconciliationService implements IncentiveProjectionReconciliationFacade {

    private final AccountLedgerService accountLedgerService;
    private final CommunityRoleService communityRoleService;

    @Override
    public ReconciliationDTO previewAccountProjection(Integer limit, String reason, Long operatorUid) {
        return accountLedgerService.previewReconciliation(limit, reason, operatorUid);
    }

    @Override
    public ReconciliationDTO reconcileAccountProjection(Integer limit, String reason, Long operatorUid) {
        return accountLedgerService.reconcile(limit, reason, operatorUid);
    }

    @Override
    public int previewExpiredRoleGrants(Integer limit, Long operatorUid) {
        return communityRoleService.countExpireDue(operatorUid, limit);
    }

    @Override
    public int reconcileExpiredRoleGrants(Integer limit, String reason, Long operatorUid) {
        return communityRoleService.expireDue(operatorUid, limit, reason);
    }

    @Override
    public int previewOverdueRewardInbox(Integer limit, Long operatorUid) {
        return accountLedgerService.countOverduePendingRewards(limit, operatorUid);
    }

    @Override
    public RewardInboxReconcileDTO reconcileOverdueRewardInbox(
            Integer limit, String reason, Long operatorUid) {
        return accountLedgerService.reconcileOverduePendingRewards(limit, reason, operatorUid);
    }
}
