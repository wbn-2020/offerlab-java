package com.offerlab.community.incentive.infrastructure;

import com.offerlab.community.incentive.infrastructure.IncentivePersistence.AccountPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.BenefitOrderPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.BenefitPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.BenefitEntitlementPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.BenefitEntitlementUsagePO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.BountyAppealPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.BountyPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.BountySubmissionPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.FreezePO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.IncentiveAppealPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.InvalidationJobPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.LedgerPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.ReconciliationRunPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.RecoveryDebtPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.RewardBatchPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.RewardInboxPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.RewardRulePO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.RiskFindingPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.RoleApplicationPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.RoleDefinitionPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.RoleGrantPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.ThankTicketPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface IncentiveMapper {

    @Insert("""
            INSERT IGNORE INTO t_incentive_account(
                id, user_id, account_type, domain_code, total_balance, available_balance,
                frozen_balance, recovery_debt, account_status, version
            ) VALUES (#{id}, #{userId}, #{accountType}, #{domainCode}, 0, 0, 0, 0, 'ACTIVE', 0)
            """)
    int insertAccountIfAbsent(@Param("id") Long id, @Param("userId") Long userId,
                              @Param("accountType") String accountType, @Param("domainCode") String domainCode);

    @Select("""
            SELECT * FROM t_incentive_account
            WHERE user_id = #{userId} AND account_type = #{accountType} AND domain_code = #{domainCode}
            LIMIT 1
            """)
    AccountPO selectAccount(@Param("userId") Long userId, @Param("accountType") String accountType,
                            @Param("domainCode") String domainCode);

    @Select("""
            SELECT * FROM t_incentive_account
            WHERE user_id = #{userId} AND account_type = #{accountType} AND domain_code = #{domainCode}
            LIMIT 1 FOR UPDATE
            """)
    AccountPO lockAccount(@Param("userId") Long userId, @Param("accountType") String accountType,
                          @Param("domainCode") String domainCode);

    @Select("SELECT * FROM t_incentive_account WHERE id = #{id} LIMIT 1 FOR UPDATE")
    AccountPO lockAccountById(@Param("id") Long id);

    @Select("""
            SELECT * FROM t_incentive_account
            WHERE user_id = #{userId}
            ORDER BY account_type, domain_code
            LIMIT #{limit}
            """)
    List<AccountPO> selectAccounts(@Param("userId") Long userId, @Param("limit") int limit);

    @Update("""
            UPDATE t_incentive_account
            SET total_balance = total_balance + #{deltaTotal},
                available_balance = available_balance + #{deltaAvailable},
                frozen_balance = frozen_balance + #{deltaFrozen},
                account_status = #{status},
                version = version + 1
            WHERE id = #{id}
              AND version = #{version}
              AND total_balance + #{deltaTotal} >= 0
              AND available_balance + #{deltaAvailable} >= 0
              AND frozen_balance + #{deltaFrozen} >= 0
            """)
    int updateAccount(@Param("id") Long id, @Param("version") Long version,
                      @Param("deltaTotal") long deltaTotal, @Param("deltaAvailable") long deltaAvailable,
                      @Param("deltaFrozen") long deltaFrozen, @Param("status") String status);

    @Update("UPDATE t_incentive_account SET account_status = #{status}, version = version + 1 WHERE id = #{id}")
    int updateAccountStatus(@Param("id") Long id, @Param("status") String status);

    @Update("""
            UPDATE t_incentive_account
            SET recovery_debt = #{debt}, account_status = #{status}, version = version + 1
            WHERE id = #{id}
            """)
    int updateAccountRecoveryState(@Param("id") Long id, @Param("debt") long debt,
                                   @Param("status") String status);

    @Select("SELECT COUNT(*) FROM t_user_account WHERE id = #{userId} AND is_deleted = 0")
    int countActiveUser(@Param("userId") Long userId);

    @Insert("""
            INSERT INTO t_incentive_ledger(
                id, account_id, user_id, account_type, domain_code, entry_type,
                delta_total, delta_available, delta_frozen, total_after, available_after, frozen_after,
                idempotency_key, request_fingerprint, reference_type, reference_id, rule_code, rule_version, batch_id,
                reversed_entry_id, reason, operator_uid, audit_json
            ) VALUES (
                #{id}, #{accountId}, #{userId}, #{accountType}, #{domainCode}, #{entryType},
                #{deltaTotal}, #{deltaAvailable}, #{deltaFrozen}, #{totalAfter}, #{availableAfter}, #{frozenAfter},
                #{idempotencyKey}, #{requestFingerprint}, #{referenceType}, #{referenceId}, #{ruleCode}, #{ruleVersion}, #{batchId},
                #{reversedEntryId}, #{reason}, #{operatorUid}, #{auditJson}
            )
            """)
    int insertLedger(LedgerPO po);

    @Select("SELECT * FROM t_incentive_ledger WHERE id = #{id}")
    LedgerPO selectLedgerById(@Param("id") Long id);

    @Select("SELECT * FROM t_incentive_ledger WHERE idempotency_key = #{key} LIMIT 1")
    LedgerPO selectLedgerByIdempotency(@Param("key") String key);

    @Select("""
            SELECT * FROM t_incentive_ledger
            WHERE user_id = #{userId}
            ORDER BY create_time DESC, id DESC
            LIMIT #{offset}, #{limit}
            """)
    List<LedgerPO> selectLedgerPage(@Param("userId") Long userId, @Param("offset") int offset,
                                    @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM t_incentive_ledger WHERE user_id = #{userId}")
    long countLedger(@Param("userId") Long userId);

    @Select("""
            SELECT COALESCE(SUM(delta_total), 0) FROM t_incentive_ledger
            WHERE user_id = #{userId} AND rule_code = #{ruleCode} AND rule_version = #{ruleVersion}
              AND create_time >= #{fromTime}
            """)
    long sumRuleRewardsSince(@Param("userId") Long userId, @Param("ruleCode") String ruleCode,
                             @Param("ruleVersion") Integer ruleVersion, @Param("fromTime") LocalDateTime fromTime);

    @Select("""
            SELECT COALESCE(SUM(delta_total), 0) FROM t_incentive_ledger
            WHERE user_id = #{userId} AND rule_code = #{ruleCode} AND rule_version = #{ruleVersion}
            """)
    long sumRuleRewards(@Param("userId") Long userId, @Param("ruleCode") String ruleCode,
                        @Param("ruleVersion") Integer ruleVersion);

    @Insert("""
            INSERT IGNORE INTO t_incentive_reward_guard(
                recipient_uid, rule_code, rule_version, counter_date, daily_awarded, lifetime_awarded, version
            ) VALUES (#{userId}, #{ruleCode}, #{ruleVersion}, CURRENT_DATE, 0, 0, 0)
            """)
    int insertRewardGuardIfAbsent(@Param("userId") Long userId, @Param("ruleCode") String ruleCode,
                                  @Param("ruleVersion") Integer ruleVersion);

    @Select("""
            SELECT counter_date AS counterDate, daily_awarded AS dailyAwarded,
                   lifetime_awarded AS lifetimeAwarded, version,
                   CURRENT_DATE AS dbToday
            FROM t_incentive_reward_guard
            WHERE recipient_uid = #{userId} AND rule_code = #{ruleCode} AND rule_version = #{ruleVersion}
            FOR UPDATE
            """)
    Map<String, Object> lockRewardGuard(@Param("userId") Long userId, @Param("ruleCode") String ruleCode,
                                        @Param("ruleVersion") Integer ruleVersion);

    @Update("""
            UPDATE t_incentive_reward_guard
            SET counter_date = CURRENT_DATE,
                daily_awarded = CASE WHEN counter_date = CURRENT_DATE
                    THEN daily_awarded + #{amount} ELSE #{amount} END,
                lifetime_awarded = lifetime_awarded + #{amount},
                version = version + 1
            WHERE recipient_uid = #{userId} AND rule_code = #{ruleCode} AND rule_version = #{ruleVersion}
            """)
    int incrementRewardGuard(@Param("userId") Long userId, @Param("ruleCode") String ruleCode,
                             @Param("ruleVersion") Integer ruleVersion, @Param("amount") long amount);

    @Insert("""
            INSERT INTO t_incentive_reward_rule(
                id, rule_code, rule_version, account_type, domain_code, reward_amount,
                daily_user_cap, lifetime_user_cap, enabled, valid_from, valid_until, created_by, change_reason
            ) VALUES (
                #{id}, #{ruleCode}, #{ruleVersion}, #{accountType}, #{domainCode}, #{rewardAmount},
                #{dailyUserCap}, #{lifetimeUserCap}, #{enabled}, #{validFrom}, #{validUntil}, #{createdBy}, #{changeReason}
            )
            """)
    int insertRewardRule(RewardRulePO po);

    @Select("""
            SELECT * FROM t_incentive_reward_rule
            WHERE rule_code = #{code} AND rule_version = #{version}
            LIMIT 1
            """)
    RewardRulePO selectRewardRule(@Param("code") String code, @Param("version") Integer version);

    @Insert("""
            INSERT IGNORE INTO t_incentive_reward_inbox(
                id, stable_key, event_type, request_fingerprint, recipient_uid, domain_code, event_domain_code, rule_code, rule_version,
                source_reference_type, source_reference_id, parent_reference_type, parent_reference_id,
                payload_json, received_by, receive_reason
            ) VALUES (
                #{id}, #{stableKey}, #{eventType}, #{requestFingerprint}, #{recipientUid}, #{domainCode}, #{eventDomainCode}, #{ruleCode}, #{ruleVersion},
                #{sourceReferenceType}, #{sourceReferenceId}, #{parentReferenceType}, #{parentReferenceId},
                #{payloadJson}, #{receivedBy}, #{receiveReason}
            )
            """)
    int insertRewardInbox(RewardInboxPO po);

    @Select("SELECT * FROM t_incentive_reward_inbox WHERE stable_key = #{key} LIMIT 1")
    RewardInboxPO selectInboxByStableKey(@Param("key") String stableKey);

    @Select("SELECT * FROM t_incentive_reward_inbox WHERE id = #{id} LIMIT 1 FOR UPDATE")
    RewardInboxPO lockInbox(@Param("id") Long id);

    @Select("""
            SELECT l.*
            FROM t_incentive_reward_inbox i
            JOIN t_incentive_ledger l ON l.id = i.processed_entry_id
            WHERE i.inbox_status = 'APPLIED'
              AND (
                  (#{referenceType} NOT IN ('COMMENT', 'COMMENT_BRANCH')
                   AND (
                       (i.source_reference_type = #{referenceType} AND i.source_reference_id = #{referenceId})
                       OR (i.parent_reference_type = #{referenceType} AND i.parent_reference_id = #{referenceId})
                   ))
                  OR (
                      #{referenceType} IN ('COMMENT', 'COMMENT_BRANCH')
                      AND i.source_reference_type = 'COMMENT_HELPFUL_THRESHOLD_REACHED'
                      AND i.source_reference_id LIKE 'comment:%'
                      AND EXISTS (
                          SELECT 1
                          FROM t_int_comment c
                          WHERE c.id = CAST(#{referenceId} AS UNSIGNED)
                            AND (
                                CAST(SUBSTRING(i.source_reference_id, 9) AS UNSIGNED) = c.id
                                OR (
                                    #{referenceType} = 'COMMENT_BRANCH'
                                    AND CAST(SUBSTRING(i.source_reference_id, 9) AS UNSIGNED) = c.root_id
                                )
                            )
                      )
                  )
              )
              AND l.entry_type = 'REWARD' AND l.delta_total > 0
              AND l.id > #{cursor}
              AND NOT EXISTS (
                  SELECT 1 FROM t_incentive_ledger r
                  WHERE r.reversed_entry_id = l.id AND r.entry_type = 'REVERSAL'
              )
            ORDER BY l.id
            LIMIT #{limit}
            """)
    List<LedgerPO> selectAppliedRewardLedgersBySource(@Param("referenceType") String referenceType,
                                                      @Param("referenceId") String referenceId,
                                                      @Param("cursor") long cursor,
                                                      @Param("limit") int limit);

    @Select("""
            SELECT id FROM t_incentive_reward_inbox
            WHERE inbox_status = 'PENDING' AND rule_code = #{code} AND rule_version = #{version}
            ORDER BY id
            LIMIT #{limit}
            """)
    List<Long> selectPendingInboxIds(@Param("code") String code, @Param("version") Integer version,
                                     @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM (
                SELECT id
                FROM t_incentive_reward_inbox
                WHERE inbox_status = 'PENDING'
                  AND create_time <= #{cutoff}
                ORDER BY create_time, id
                LIMIT #{cap}
            ) bounded
            """)
    int countOverduePendingInbox(@Param("cutoff") LocalDateTime cutoff,
                                 @Param("cap") int cap);

    @Select("""
            SELECT id
            FROM t_incentive_reward_inbox
            WHERE inbox_status = 'PENDING'
              AND create_time <= #{cutoff}
            ORDER BY create_time, id
            LIMIT #{limit}
            FOR UPDATE SKIP LOCKED
            """)
    List<Long> selectOverduePendingInboxIdsForUpdate(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit);

    @Update("""
            UPDATE t_incentive_reward_inbox
            SET inbox_status = #{status}, batch_id = #{batchId}, attempt_count = attempt_count + 1,
                processed_entry_id = #{entryId}, error_message = #{error},
                processed_time = CASE WHEN #{status} IN ('APPLIED', 'REJECTED') THEN CURRENT_TIMESTAMP(3) ELSE processed_time END
            WHERE id = #{id}
            """)
    int updateInbox(@Param("id") Long id, @Param("status") String status, @Param("batchId") Long batchId,
                    @Param("entryId") Long entryId, @Param("error") String error);

    @Insert("""
            INSERT IGNORE INTO t_incentive_invalidation_job(
                id, job_key, reference_type, reference_id, invalidation_reason,
                job_status, cursor_value, processed_count, attempt_count
            ) VALUES (
                #{id}, #{jobKey}, #{referenceType}, #{referenceId}, #{invalidationReason},
                'PENDING', 0, 0, 0
            )
            """)
    int insertInvalidationJob(InvalidationJobPO po);

    @Select("SELECT * FROM t_incentive_invalidation_job WHERE job_key = #{jobKey} LIMIT 1")
    InvalidationJobPO selectInvalidationJobByKey(@Param("jobKey") String jobKey);

    @Select("SELECT * FROM t_incentive_invalidation_job WHERE job_key = #{jobKey} LIMIT 1 FOR UPDATE")
    InvalidationJobPO lockInvalidationJobByKey(@Param("jobKey") String jobKey);

    @Select("SELECT * FROM t_incentive_invalidation_job WHERE id = #{id} FOR UPDATE")
    InvalidationJobPO lockInvalidationJob(@Param("id") Long id);

    @Select("""
            SELECT id FROM t_incentive_invalidation_job
            WHERE job_status = 'PENDING'
               OR (job_status = 'RUNNING' AND lease_until < CURRENT_TIMESTAMP(3))
            ORDER BY update_time, id
            LIMIT #{limit} FOR UPDATE SKIP LOCKED
            """)
    List<Long> selectInvalidationJobsForClaim(@Param("limit") int limit);

    @Update("""
            UPDATE t_incentive_invalidation_job
            SET job_status = 'RUNNING', lease_owner = #{owner},
                lease_until = DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL #{leaseSeconds} SECOND),
                attempt_count = attempt_count + 1, last_error = NULL
            WHERE id = #{id}
              AND (job_status = 'PENDING'
                   OR (job_status = 'RUNNING' AND lease_until < CURRENT_TIMESTAMP(3)))
            """)
    int claimInvalidationJob(@Param("id") Long id, @Param("owner") String owner,
                             @Param("leaseSeconds") int leaseSeconds);

    @Update("""
            UPDATE t_incentive_invalidation_job
            SET job_status = 'PENDING', cursor_value = #{cursor},
                processed_count = processed_count + #{processed},
                lease_owner = NULL, lease_until = NULL, last_error = NULL
            WHERE id = #{id} AND job_status = 'RUNNING' AND lease_owner = #{owner}
            """)
    int advanceInvalidationJob(@Param("id") Long id, @Param("owner") String owner,
                               @Param("cursor") long cursor, @Param("processed") int processed);

    @Update("""
            UPDATE t_incentive_invalidation_job
            SET job_status = 'COMPLETED', cursor_value = #{cursor},
                processed_count = processed_count + #{processed},
                lease_owner = NULL, lease_until = NULL, last_error = NULL,
                completed_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id} AND job_status = 'RUNNING' AND lease_owner = #{owner}
            """)
    int completeInvalidationJob(@Param("id") Long id, @Param("owner") String owner,
                                @Param("cursor") long cursor, @Param("processed") int processed);

    @Update("""
            UPDATE t_incentive_invalidation_job
            SET job_status = 'PENDING', lease_owner = NULL, lease_until = NULL,
                last_error = #{error}
            WHERE id = #{id} AND job_status = 'RUNNING' AND lease_owner = #{owner}
            """)
    int releaseInvalidationJob(@Param("id") Long id, @Param("owner") String owner,
                               @Param("error") String error);

    @Update("""
            UPDATE t_incentive_reward_inbox
            SET inbox_status = 'REJECTED', error_message = 'TRUSTED_SOURCE_INVALIDATED',
                processed_time = CURRENT_TIMESTAMP(3)
            WHERE inbox_status = 'PENDING'
              AND (
                  (#{referenceType} NOT IN ('COMMENT', 'COMMENT_BRANCH')
                   AND (
                       (source_reference_type = #{referenceType} AND source_reference_id = #{referenceId})
                       OR (parent_reference_type = #{referenceType} AND parent_reference_id = #{referenceId})
                   ))
                  OR (
                      #{referenceType} IN ('COMMENT', 'COMMENT_BRANCH')
                      AND source_reference_type = 'COMMENT_HELPFUL_THRESHOLD_REACHED'
                      AND source_reference_id LIKE 'comment:%'
                      AND EXISTS (
                          SELECT 1
                          FROM t_int_comment c
                          WHERE c.id = CAST(#{referenceId} AS UNSIGNED)
                            AND (
                                CAST(SUBSTRING(source_reference_id, 9) AS UNSIGNED) = c.id
                                OR (
                                    #{referenceType} = 'COMMENT_BRANCH'
                                    AND CAST(SUBSTRING(source_reference_id, 9) AS UNSIGNED) = c.root_id
                                )
                            )
                      )
                  )
              )
            """)
    int rejectPendingInboxByInvalidation(@Param("referenceType") String referenceType,
                                          @Param("referenceId") String referenceId);

    @Select("""
            SELECT COUNT(*)
            FROM t_incentive_invalidation_job
            WHERE reference_type = #{referenceType} AND reference_id = #{referenceId}
            """)
    int countInvalidationJobs(@Param("referenceType") String referenceType,
                              @Param("referenceId") String referenceId);

    @Select("""
            SELECT COUNT(*)
            FROM t_incentive_invalidation_job j
            JOIN t_int_comment c ON c.id = CAST(#{commentId} AS UNSIGNED)
            WHERE j.reference_type = 'COMMENT_BRANCH'
              AND (
                  CAST(j.reference_id AS UNSIGNED) = c.id
                  OR (c.root_id > 0 AND CAST(j.reference_id AS UNSIGNED) = c.root_id)
              )
            """)
    int countCommentBranchInvalidation(@Param("commentId") String commentId);

    @Insert("""
            INSERT INTO t_incentive_reward_batch(
                id, batch_key, rule_code, rule_version, batch_status, requested_count,
                applied_count, rejected_count, failed_count, created_by, action_reason
            ) VALUES (
                #{id}, #{batchKey}, #{ruleCode}, #{ruleVersion}, #{batchStatus}, #{requestedCount},
                #{appliedCount}, #{rejectedCount}, #{failedCount}, #{createdBy}, #{actionReason}
            )
            """)
    int insertRewardBatch(RewardBatchPO po);

    @Select("SELECT * FROM t_incentive_reward_batch WHERE batch_key = #{key} LIMIT 1")
    RewardBatchPO selectBatchByKey(@Param("key") String key);

    @Update("""
            UPDATE t_incentive_reward_batch
            SET batch_status = #{status}, applied_count = #{applied}, rejected_count = #{rejected},
                failed_count = #{failed}, finish_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    int finishBatch(@Param("id") Long id, @Param("status") String status, @Param("applied") int applied,
                    @Param("rejected") int rejected, @Param("failed") int failed);

    @Insert("""
            INSERT INTO t_incentive_freeze_record(
                id, account_id, user_id, freeze_amount, freeze_status, blocks_spending, freeze_entry_id,
                operator_uid, action_reason
            ) VALUES (
                #{id}, #{accountId}, #{userId}, #{freezeAmount}, #{freezeStatus}, #{blocksSpending}, #{freezeEntryId},
                #{operatorUid}, #{actionReason}
            )
            """)
    int insertFreeze(FreezePO po);

    @Select("SELECT * FROM t_incentive_freeze_record WHERE id = #{id} LIMIT 1 FOR UPDATE")
    FreezePO lockFreeze(@Param("id") Long id);

    @Select("SELECT * FROM t_incentive_freeze_record WHERE freeze_entry_id = #{entryId} LIMIT 1")
    FreezePO selectFreezeByEntry(@Param("entryId") Long entryId);

    @Update("""
            UPDATE t_incentive_freeze_record
            SET freeze_status = 'RELEASED', release_entry_id = #{entryId}, release_reason = #{reason}
            WHERE id = #{id} AND freeze_status = 'ACTIVE'
            """)
    int releaseFreeze(@Param("id") Long id, @Param("entryId") Long entryId, @Param("reason") String reason);

    @Select("""
            SELECT COUNT(*) FROM t_incentive_freeze_record
            WHERE account_id = #{accountId} AND freeze_status = 'ACTIVE' AND blocks_spending = 1
            """)
    int countActiveBlockingFreezes(@Param("accountId") Long accountId);

    @Select("""
            SELECT COALESCE(SUM(freeze_amount), 0) FROM t_incentive_freeze_record
            WHERE account_id = #{accountId} AND freeze_status = 'ACTIVE' AND blocks_spending = 1
            """)
    long sumActiveBlockingFreezeAmount(@Param("accountId") Long accountId);

    @Select("SELECT * FROM t_incentive_ledger WHERE reversed_entry_id = #{entryId} LIMIT 1")
    LedgerPO selectReversalByOriginal(@Param("entryId") Long entryId);

    @Select("SELECT * FROM t_incentive_ledger WHERE reversed_entry_id = #{entryId} LIMIT 1 FOR UPDATE")
    LedgerPO lockReversalByOriginal(@Param("entryId") Long entryId);

    @Insert("""
            INSERT INTO t_incentive_recovery_debt(
                id, account_id, user_id, original_entry_id, reversal_entry_id,
                original_amount, recovered_amount, outstanding_amount, debt_status, action_reason
            ) VALUES (
                #{id}, #{accountId}, #{userId}, #{originalEntryId}, #{reversalEntryId},
                #{originalAmount}, #{recoveredAmount}, #{outstandingAmount}, #{debtStatus}, #{actionReason}
            )
            """)
    int insertRecoveryDebt(RecoveryDebtPO po);

    @Select("""
            SELECT * FROM t_incentive_recovery_debt
            WHERE original_entry_id = #{originalEntryId} LIMIT 1 FOR UPDATE
            """)
    RecoveryDebtPO lockRecoveryDebtByOriginal(@Param("originalEntryId") Long originalEntryId);

    @Select("""
            SELECT * FROM t_incentive_recovery_debt
            WHERE original_entry_id = #{originalEntryId} LIMIT 1
            """)
    RecoveryDebtPO selectRecoveryDebtByOriginal(@Param("originalEntryId") Long originalEntryId);

    @Select("""
            SELECT COALESCE(SUM(-delta_total), 0) AS restoreTotal,
                   COALESCE(SUM(-delta_available), 0) AS restoreAvailable,
                   COALESCE(SUM(-delta_frozen), 0) AS restoreFrozen
            FROM t_incentive_ledger
            WHERE id = #{reversalEntryId}
               OR (#{debtId} IS NOT NULL AND entry_type = 'DEBT_RECOVERY'
                   AND reference_type = 'RECOVERY_DEBT'
                   AND reference_id = CAST(#{debtId} AS CHAR))
            """)
    Map<String, Object> selectAppealRecoveryTotals(@Param("reversalEntryId") Long reversalEntryId,
                                                    @Param("debtId") Long debtId);

    @Select("""
            SELECT * FROM t_incentive_recovery_debt
            WHERE account_id = #{accountId} AND debt_status = 'PENDING' AND outstanding_amount > 0
            ORDER BY id LIMIT #{limit} FOR UPDATE
            """)
    List<RecoveryDebtPO> lockPendingRecoveryDebts(@Param("accountId") Long accountId,
                                                  @Param("limit") int limit);

    @Update("""
            UPDATE t_incentive_recovery_debt
            SET recovered_amount = recovered_amount + #{amount},
                outstanding_amount = outstanding_amount - #{amount},
                debt_status = CASE WHEN outstanding_amount = #{amount} THEN 'SETTLED' ELSE 'PENDING' END,
                settled_time = CASE WHEN outstanding_amount = #{amount}
                    THEN CURRENT_TIMESTAMP(3) ELSE settled_time END
            WHERE id = #{id} AND debt_status = 'PENDING' AND outstanding_amount >= #{amount}
            """)
    int recoverDebt(@Param("id") Long id, @Param("amount") long amount);

    @Update("""
            UPDATE t_incentive_recovery_debt
            SET debt_status = 'CANCELLED', outstanding_amount = 0,
                settled_time = CURRENT_TIMESTAMP(3), action_reason = #{reason}
            WHERE original_entry_id = #{originalEntryId}
              AND debt_status = 'PENDING'
            """)
    int cancelRecoveryDebt(@Param("originalEntryId") Long originalEntryId,
                           @Param("reason") String reason);

    @Select("""
            SELECT COUNT(*) FROM t_incentive_recovery_debt
            WHERE account_id = #{accountId} AND debt_status = 'PENDING' AND outstanding_amount > 0
            """)
    int countPendingRecoveryDebts(@Param("accountId") Long accountId);

    @Select("""
            SELECT COALESCE(SUM(outstanding_amount), 0) FROM t_incentive_recovery_debt
            WHERE account_id = #{accountId} AND debt_status = 'PENDING'
            """)
    long sumPendingRecoveryDebt(@Param("accountId") Long accountId);

    @Insert("""
            INSERT IGNORE INTO t_incentive_reconciliation_cursor(
                id, last_account_id, cycle_no, version
            ) VALUES (1, 0, 1, 0)
            """)
    int insertReconciliationCursorIfAbsent();

    @Select("""
            SELECT last_account_id AS lastAccountId, cycle_no AS cycleNo, version
            FROM t_incentive_reconciliation_cursor WHERE id = 1 FOR UPDATE
            """)
    Map<String, Object> lockReconciliationCursor();

    @Select("""
            SELECT last_account_id AS lastAccountId, cycle_no AS cycleNo, version
            FROM t_incentive_reconciliation_cursor WHERE id = 1
            """)
    Map<String, Object> selectReconciliationCursor();

    @Insert("""
            INSERT INTO t_incentive_reconciliation_run(
                id, run_status, scanned_count, mismatch_count, total_absolute_difference,
                cycle_no, cursor_start_account_id, cursor_end_account_id,
                next_cursor_account_id, coverage_complete, operator_uid, action_reason
            ) VALUES (
                #{id}, 'RUNNING', 0, 0, 0, #{cycleNo}, #{cursorStart}, #{cursorStart},
                #{cursorStart}, 0, #{operatorUid}, #{reason}
            )
            """)
    int insertReconciliationRun(@Param("id") Long id, @Param("cycleNo") long cycleNo,
                                @Param("cursorStart") long cursorStart,
                                @Param("operatorUid") Long operatorUid, @Param("reason") String reason);

    @Select("""
            SELECT a.id AS accountId,
                   a.total_balance AS projectedTotal,
                   COALESCE(SUM(l.delta_total), 0) AS ledgerTotal,
                   a.available_balance AS projectedAvailable,
                   COALESCE(SUM(l.delta_available), 0) AS ledgerAvailable,
                   a.frozen_balance AS projectedFrozen,
                   COALESCE(SUM(l.delta_frozen), 0) AS ledgerFrozen
            FROM (
                SELECT id, total_balance, available_balance, frozen_balance
                FROM t_incentive_account
                WHERE id > #{cursor}
                ORDER BY id
                LIMIT #{limit}
            ) a
            LEFT JOIN t_incentive_ledger l ON l.account_id = a.id
            GROUP BY a.id, a.total_balance, a.available_balance, a.frozen_balance
            ORDER BY a.id
            """)
    List<Map<String, Object>> selectReconciliationRows(@Param("cursor") long cursor,
                                                        @Param("limit") int limit);

    @Insert("""
            INSERT INTO t_incentive_reconciliation_item(
                id, run_id, account_id, projected_total, ledger_total, projected_available,
                ledger_available, projected_frozen, ledger_frozen, absolute_difference
            ) VALUES (
                #{id}, #{runId}, #{accountId}, #{projectedTotal}, #{ledgerTotal}, #{projectedAvailable},
                #{ledgerAvailable}, #{projectedFrozen}, #{ledgerFrozen}, #{difference}
            )
            """)
    int insertReconciliationItem(@Param("id") Long id, @Param("runId") Long runId,
                                 @Param("accountId") Long accountId, @Param("projectedTotal") long projectedTotal,
                                 @Param("ledgerTotal") long ledgerTotal, @Param("projectedAvailable") long projectedAvailable,
                                 @Param("ledgerAvailable") long ledgerAvailable, @Param("projectedFrozen") long projectedFrozen,
                                 @Param("ledgerFrozen") long ledgerFrozen, @Param("difference") long difference);

    @Update("""
            UPDATE t_incentive_reconciliation_run
            SET run_status = 'COMPLETED', scanned_count = #{scanned}, mismatch_count = #{mismatches},
                total_absolute_difference = #{difference}, cursor_end_account_id = #{cursorEnd},
                next_cursor_account_id = #{nextCursor}, coverage_complete = #{coverageComplete},
                finish_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    int finishReconciliation(@Param("id") Long id, @Param("scanned") int scanned,
                             @Param("mismatches") int mismatches, @Param("difference") long difference,
                             @Param("cursorEnd") long cursorEnd, @Param("nextCursor") long nextCursor,
                             @Param("coverageComplete") int coverageComplete);

    @Update("""
            UPDATE t_incentive_reconciliation_cursor
            SET last_account_id = #{nextCursor},
                cycle_no = CASE WHEN #{coverageComplete} = 1 THEN cycle_no + 1 ELSE cycle_no END,
                version = version + 1
            WHERE id = 1
            """)
    int advanceReconciliationCursor(@Param("nextCursor") long nextCursor,
                                    @Param("coverageComplete") int coverageComplete);

    @Select("SELECT * FROM t_incentive_reconciliation_run WHERE id = #{id}")
    ReconciliationRunPO selectReconciliationRun(@Param("id") Long id);

    @Select("""
            SELECT * FROM t_incentive_reconciliation_run
            ORDER BY create_time DESC, id DESC LIMIT #{limit}
            """)
    List<ReconciliationRunPO> selectReconciliationRuns(@Param("limit") int limit);

    @Insert("""
            INSERT INTO t_incentive_appeal(
                id, appellant_uid, target_type, target_id, related_ledger_id,
                related_recovery_debt_id, original_operator_uid,
                appeal_reason, appeal_status
            ) VALUES (
                #{id}, #{appellantUid}, #{targetType}, #{targetId}, #{relatedLedgerId},
                #{relatedRecoveryDebtId}, #{originalOperatorUid},
                #{appealReason}, #{appealStatus}
            )
            """)
    int insertIncentiveAppeal(IncentiveAppealPO po);

    @Select("SELECT * FROM t_incentive_appeal WHERE id = #{id}")
    IncentiveAppealPO selectIncentiveAppeal(@Param("id") Long id);

    @Select("SELECT * FROM t_incentive_appeal WHERE id = #{id} FOR UPDATE")
    IncentiveAppealPO lockIncentiveAppeal(@Param("id") Long id);

    @Select("""
            SELECT * FROM t_incentive_appeal
            WHERE appellant_uid = #{userId}
            ORDER BY create_time DESC, id DESC LIMIT #{offset}, #{limit}
            """)
    List<IncentiveAppealPO> selectUserIncentiveAppeals(@Param("userId") Long userId,
                                                       @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM t_incentive_appeal WHERE appellant_uid = #{userId}")
    long countUserIncentiveAppeals(@Param("userId") Long userId);

    @Select("""
            SELECT * FROM t_incentive_appeal
            WHERE (#{status} IS NULL OR appeal_status = #{status})
            ORDER BY update_time DESC, id DESC LIMIT #{offset}, #{limit}
            """)
    List<IncentiveAppealPO> selectIncentiveAppealQueue(@Param("status") String status,
                                                       @Param("offset") int offset, @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*) FROM t_incentive_appeal
            WHERE (#{status} IS NULL OR appeal_status = #{status})
            """)
    long countIncentiveAppealQueue(@Param("status") String status);

    @Update("""
            UPDATE t_incentive_appeal
            SET appeal_status = #{status}, reviewer_uid = #{reviewerUid},
                review_reason = #{reason}, restore_entry_id = #{restoreEntryId}
            WHERE id = #{id} AND appeal_status = 'SUBMITTED'
            """)
    int reviewIncentiveAppeal(@Param("id") Long id, @Param("status") String status,
                              @Param("reviewerUid") Long reviewerUid, @Param("reason") String reason,
                              @Param("restoreEntryId") Long restoreEntryId);

    @Insert("""
            INSERT IGNORE INTO t_incentive_risk_scan_cursor(
                scan_type, cursor_value, cycle_no, version
            ) VALUES (#{scanType}, 0, 1, 0)
            """)
    int insertRiskScanCursorIfAbsent(@Param("scanType") String scanType);

    @Select("""
            SELECT cursor_value AS cursorValue, cycle_no AS cycleNo
            FROM t_incentive_risk_scan_cursor WHERE scan_type = #{scanType} FOR UPDATE
            """)
    Map<String, Object> lockRiskScanCursor(@Param("scanType") String scanType);

    @Insert("""
            INSERT INTO t_incentive_risk_scan_run(
                id, scan_type, cycle_no, cursor_start, cursor_end, next_cursor,
                coverage_complete, scanned_count, finding_count, run_status, operator_uid, action_reason
            ) VALUES (
                #{id}, #{scanType}, #{cycleNo}, #{cursorStart}, #{cursorStart}, #{cursorStart},
                0, 0, 0, 'RUNNING', #{operatorUid}, #{reason}
            )
            """)
    int insertRiskScanRun(@Param("id") Long id, @Param("scanType") String scanType,
                          @Param("cycleNo") long cycleNo, @Param("cursorStart") long cursorStart,
                          @Param("operatorUid") Long operatorUid, @Param("reason") String reason);

    @Select("""
            SELECT user_id AS subjectId, SUM(delta_total) AS metricValue,
                   COUNT(*) AS sampleCount, MAX(id) AS cursorId
            FROM t_incentive_ledger
            WHERE user_id > #{cursor} AND create_time >= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 HOUR)
              AND entry_type IN ('REWARD', 'PLATFORM_GRANT') AND delta_total > 0
            GROUP BY user_id
            ORDER BY user_id
            LIMIT #{limit}
            """)
    List<Map<String, Object>> selectHourlyRewardRiskCandidates(@Param("cursor") long cursor,
                                                               @Param("limit") int limit);

    @Select("""
            SELECT MAX(i.id) AS cursorId, i.source_reference_type AS referenceType,
                   i.source_reference_id AS referenceId, i.recipient_uid AS recipientUid,
                   i.rule_code AS ruleCode, i.rule_version AS ruleVersion,
                   COUNT(*) AS metricValue,
                   COALESCE(SUM(l.delta_total), 0) AS totalAwarded
            FROM t_incentive_reward_inbox i
            JOIN t_incentive_ledger l ON l.id = i.processed_entry_id
            WHERE i.inbox_status = 'APPLIED'
              AND i.source_reference_type IS NOT NULL AND i.source_reference_id IS NOT NULL
              AND l.entry_type = 'REWARD' AND l.delta_total > 0
            GROUP BY i.source_reference_type, i.source_reference_id,
                     i.recipient_uid, i.rule_code, i.rule_version
            HAVING COUNT(*) > 1 AND MAX(i.id) > #{cursor}
            ORDER BY cursorId
            LIMIT #{limit}
            """)
    List<Map<String, Object>> selectDuplicateReferenceRiskCandidates(@Param("cursor") long cursor,
                                                                     @Param("limit") int limit);

    @Select("""
            SELECT totals.totalAwarded, COALESCE(top_user.topAwarded, 0) AS topAwarded,
                   top_user.topUserId
            FROM (
                SELECT COALESCE(SUM(delta_total), 0) AS totalAwarded
                FROM t_incentive_ledger
                WHERE create_time >= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 24 HOUR)
                  AND entry_type IN ('REWARD', 'PLATFORM_GRANT') AND delta_total > 0
            ) totals
            LEFT JOIN (
                SELECT user_id AS topUserId, SUM(delta_total) AS topAwarded
                FROM t_incentive_ledger
                WHERE create_time >= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 24 HOUR)
                  AND entry_type IN ('REWARD', 'PLATFORM_GRANT') AND delta_total > 0
                GROUP BY user_id
                ORDER BY topAwarded DESC, user_id
                LIMIT 1
            ) top_user ON 1 = 1
            """)
    Map<String, Object> selectTopUserConcentrationSnapshot();

    @Select("""
            SELECT i.event_domain_code AS domainCode,
                   COUNT(*) AS contributionCount,
                   COALESCE(SUM(l.delta_total), 0) AS metricValue
            FROM t_incentive_reward_inbox i
            JOIN t_incentive_ledger l ON l.id = i.processed_entry_id
            WHERE i.inbox_status = 'APPLIED' AND i.event_domain_code IS NOT NULL
              AND l.account_type = 'POINT' AND l.delta_total > 0
            GROUP BY i.event_domain_code
            ORDER BY i.event_domain_code
            LIMIT #{limit}
            """)
    List<Map<String, Object>> selectDomainRewardContributionSnapshots(@Param("limit") int limit);

    @Insert("""
            INSERT IGNORE INTO t_incentive_risk_finding(
                id, stable_key, finding_type, subject_type, subject_id, domain_code,
                severity, evaluation_status, finding_status, metric_value, threshold_value,
                evidence_json, scan_run_id
            ) VALUES (
                #{id}, #{stableKey}, #{findingType}, #{subjectType}, #{subjectId}, #{domainCode},
                #{severity}, #{evaluationStatus}, #{findingStatus}, #{metricValue}, #{thresholdValue},
                #{evidenceJson}, #{scanRunId}
            )
            """)
    int insertRiskFinding(RiskFindingPO po);

    @Update("""
            UPDATE t_incentive_risk_scan_run
            SET cursor_end = #{cursorEnd}, next_cursor = #{nextCursor},
                coverage_complete = #{coverageComplete}, scanned_count = #{scanned},
                finding_count = #{findings}, run_status = 'COMPLETED',
                finish_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    int finishRiskScanRun(@Param("id") Long id, @Param("cursorEnd") long cursorEnd,
                          @Param("nextCursor") long nextCursor,
                          @Param("coverageComplete") int coverageComplete,
                          @Param("scanned") int scanned, @Param("findings") int findings);

    @Update("""
            UPDATE t_incentive_risk_scan_cursor
            SET cursor_value = #{nextCursor},
                cycle_no = CASE WHEN #{coverageComplete} = 1 THEN cycle_no + 1 ELSE cycle_no END,
                version = version + 1
            WHERE scan_type = #{scanType}
            """)
    int advanceRiskScanCursor(@Param("scanType") String scanType, @Param("nextCursor") long nextCursor,
                              @Param("coverageComplete") int coverageComplete);

    @Select("""
            SELECT * FROM t_incentive_risk_finding
            WHERE (#{status} IS NULL OR finding_status = #{status})
            ORDER BY update_time DESC, id DESC LIMIT #{offset}, #{limit}
            """)
    List<RiskFindingPO> selectRiskFindings(@Param("status") String status,
                                           @Param("offset") int offset, @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*) FROM t_incentive_risk_finding
            WHERE (#{status} IS NULL OR finding_status = #{status})
            """)
    long countRiskFindings(@Param("status") String status);

    @Select("SELECT * FROM t_incentive_risk_finding WHERE id = #{id} FOR UPDATE")
    RiskFindingPO lockRiskFinding(@Param("id") Long id);

    @Update("""
            UPDATE t_incentive_risk_finding
            SET finding_status = #{status}, resolved_by = #{operatorUid},
                resolution_reason = #{reason}
            WHERE id = #{id} AND finding_status IN ('OPEN', 'NOT_EVALUATED')
            """)
    int resolveRiskFinding(@Param("id") Long id, @Param("status") String status,
                           @Param("operatorUid") Long operatorUid, @Param("reason") String reason);

    @Insert("""
            INSERT INTO t_virtual_benefit_catalog(
                id, benefit_code, benefit_name, description, category, delivery_type, point_cost,
                total_stock, available_stock, enabled, created_by, updated_by, action_reason
            ) VALUES (
                #{id}, #{benefitCode}, #{benefitName}, #{description}, #{category}, #{deliveryType}, #{pointCost},
                #{totalStock}, #{availableStock}, #{enabled}, #{createdBy}, #{updatedBy}, #{actionReason}
            )
            ON DUPLICATE KEY UPDATE
                benefit_name = VALUES(benefit_name), description = VALUES(description),
                category = VALUES(category), delivery_type = VALUES(delivery_type), point_cost = VALUES(point_cost),
                total_stock = VALUES(total_stock), available_stock = VALUES(available_stock),
                enabled = VALUES(enabled), updated_by = VALUES(updated_by), action_reason = VALUES(action_reason)
            """)
    int upsertBenefit(BenefitPO po);

    @Select("SELECT * FROM t_virtual_benefit_catalog WHERE id = #{id}")
    BenefitPO selectBenefit(@Param("id") Long id);

    @Select("SELECT * FROM t_virtual_benefit_catalog WHERE id = #{id} FOR UPDATE")
    BenefitPO lockBenefit(@Param("id") Long id);

    @Select("SELECT * FROM t_virtual_benefit_catalog WHERE benefit_code = #{code} LIMIT 1")
    BenefitPO selectBenefitByCode(@Param("code") String code);

    @Select("SELECT * FROM t_virtual_benefit_catalog WHERE benefit_code = #{code} LIMIT 1 FOR UPDATE")
    BenefitPO lockBenefitByCode(@Param("code") String code);

    @Select("""
            SELECT * FROM t_virtual_benefit_catalog
            WHERE enabled = 1
            ORDER BY update_time DESC, id DESC LIMIT #{offset}, #{limit}
            """)
    List<BenefitPO> selectBenefits(@Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM t_virtual_benefit_catalog WHERE enabled = 1")
    long countBenefits();

    @Select("""
            SELECT * FROM t_virtual_benefit_catalog
            ORDER BY update_time DESC, id DESC LIMIT #{offset}, #{limit}
            """)
    List<BenefitPO> selectAllBenefits(@Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM t_virtual_benefit_catalog")
    long countAllBenefits();

    @Select("SELECT COUNT(*) FROM t_virtual_benefit_order WHERE benefit_id = #{benefitId}")
    long countBenefitOrders(@Param("benefitId") Long benefitId);

    @Update("""
            UPDATE t_virtual_benefit_catalog
            SET available_stock = available_stock - #{quantity}
            WHERE id = #{id} AND enabled = 1
              AND (available_stock IS NULL OR available_stock >= #{quantity})
            """)
    int reserveBenefitStock(@Param("id") Long id, @Param("quantity") int quantity);

    @Update("""
            UPDATE t_virtual_benefit_catalog
            SET available_stock = CASE WHEN available_stock IS NULL THEN NULL ELSE LEAST(total_stock, available_stock + #{quantity}) END
            WHERE id = #{id}
            """)
    int restoreBenefitStock(@Param("id") Long id, @Param("quantity") int quantity);

    @Insert("""
            INSERT INTO t_virtual_benefit_order(
                id, order_no, idempotency_key, request_fingerprint, user_id, benefit_id, benefit_code, benefit_name,
                quantity, unit_point_cost, total_point_cost, order_status
            ) VALUES (
                #{id}, #{orderNo}, #{idempotencyKey}, #{requestFingerprint}, #{userId}, #{benefitId}, #{benefitCode}, #{benefitName},
                #{quantity}, #{unitPointCost}, #{totalPointCost}, #{orderStatus}
            )
            """)
    int insertBenefitOrder(BenefitOrderPO po);

    @Insert("""
            INSERT INTO t_virtual_benefit_entitlement(
                id, order_id, user_id, benefit_code, entitlement_type, entitlement_key,
                quantity_total, quantity_remaining, entitlement_status, reversible,
                payload_json, granted_by, grant_reason
            ) VALUES (
                #{id}, #{orderId}, #{userId}, #{benefitCode}, #{entitlementType}, #{entitlementKey},
                #{quantityTotal}, #{quantityRemaining}, #{entitlementStatus}, #{reversible},
                #{payloadJson}, #{grantedBy}, #{grantReason}
            )
            """)
    int insertBenefitEntitlement(BenefitEntitlementPO po);

    @Select("SELECT * FROM t_virtual_benefit_entitlement WHERE order_id = #{orderId} LIMIT 1")
    BenefitEntitlementPO selectEntitlementByOrder(@Param("orderId") Long orderId);

    @Select("SELECT * FROM t_virtual_benefit_entitlement WHERE order_id = #{orderId} LIMIT 1 FOR UPDATE")
    BenefitEntitlementPO lockEntitlementByOrder(@Param("orderId") Long orderId);

    @Select("SELECT * FROM t_virtual_benefit_entitlement WHERE id = #{id} FOR UPDATE")
    BenefitEntitlementPO lockBenefitEntitlement(@Param("id") Long id);

    @Select("""
            SELECT * FROM t_virtual_benefit_entitlement
            WHERE user_id = #{userId}
            ORDER BY update_time DESC, id DESC LIMIT #{offset}, #{limit}
            """)
    List<BenefitEntitlementPO> selectUserEntitlements(@Param("userId") Long userId,
                                                       @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM t_virtual_benefit_entitlement WHERE user_id = #{userId}")
    long countUserEntitlements(@Param("userId") Long userId);

    @Update("""
            UPDATE t_virtual_benefit_entitlement
            SET entitlement_status = 'REVOKED', revoked_by = #{operatorUid},
                revoke_reason = #{reason}, revoked_at = CURRENT_TIMESTAMP(3)
            WHERE order_id = #{orderId} AND entitlement_status = 'ACTIVE' AND reversible = 1
              AND quantity_remaining = quantity_total
              AND NOT EXISTS (
                  SELECT 1 FROM t_virtual_benefit_entitlement_usage u
                  WHERE u.entitlement_id = t_virtual_benefit_entitlement.id
              )
            """)
    int revokeEntitlementByOrder(@Param("orderId") Long orderId, @Param("operatorUid") Long operatorUid,
                                 @Param("reason") String reason);

    @Insert("""
            INSERT IGNORE INTO t_virtual_benefit_entitlement_usage(
                id, entitlement_id, user_id, amount, idempotency_key, request_fingerprint, action_reason
            ) VALUES (#{id}, #{entitlementId}, #{userId}, #{amount}, #{key}, #{fingerprint}, #{reason})
            """)
    int insertEntitlementUsage(@Param("id") Long id, @Param("entitlementId") Long entitlementId,
                               @Param("userId") Long userId, @Param("amount") long amount,
                               @Param("key") String key, @Param("fingerprint") String fingerprint,
                               @Param("reason") String reason);

    @Select("""
            SELECT * FROM t_virtual_benefit_entitlement_usage
            WHERE entitlement_id = #{entitlementId} AND idempotency_key = #{key}
            LIMIT 1
            """)
    BenefitEntitlementUsagePO selectEntitlementUsage(@Param("entitlementId") Long entitlementId,
                                                      @Param("key") String key);

    @Select("SELECT COUNT(*) FROM t_virtual_benefit_entitlement_usage WHERE entitlement_id = #{entitlementId}")
    long countAnyEntitlementUsage(@Param("entitlementId") Long entitlementId);

    @Update("""
            UPDATE t_virtual_benefit_entitlement
            SET quantity_remaining = quantity_remaining - #{amount},
                entitlement_status = CASE WHEN quantity_remaining = #{amount} THEN 'CONSUMED' ELSE 'ACTIVE' END
            WHERE id = #{id} AND user_id = #{userId} AND entitlement_status = 'ACTIVE'
              AND quantity_remaining >= #{amount}
            """)
    int consumeEntitlement(@Param("id") Long id, @Param("userId") Long userId,
                           @Param("amount") long amount);

    @Select("""
            SELECT * FROM t_virtual_benefit_order
            WHERE user_id = #{userId} AND idempotency_key = #{key} LIMIT 1
            """)
    BenefitOrderPO selectOrderByIdempotency(@Param("userId") Long userId, @Param("key") String key);

    @Select("""
            SELECT * FROM t_virtual_benefit_order
            WHERE user_id = #{userId} AND idempotency_key = #{key}
            LIMIT 1 FOR UPDATE
            """)
    BenefitOrderPO lockOrderByIdempotency(@Param("userId") Long userId, @Param("key") String key);

    @Select("SELECT * FROM t_virtual_benefit_order WHERE id = #{id} LIMIT 1 FOR UPDATE")
    BenefitOrderPO lockBenefitOrder(@Param("id") Long id);

    @Update("""
            UPDATE t_virtual_benefit_order
            SET order_status = #{toStatus}, reserve_entry_id = COALESCE(#{reserveEntryId}, reserve_entry_id),
                refund_entry_id = COALESCE(#{refundEntryId}, refund_entry_id),
                delivery_reference = COALESCE(#{deliveryReference}, delivery_reference),
                operator_uid = #{operatorUid}, action_reason = #{reason},
                delivered_time = CASE WHEN #{toStatus} = 'DELIVERED' THEN CURRENT_TIMESTAMP(3) ELSE delivered_time END,
                cancelled_time = CASE WHEN #{toStatus} = 'CANCELLED' THEN CURRENT_TIMESTAMP(3) ELSE cancelled_time END,
                refunded_time = CASE WHEN #{toStatus} = 'REFUNDED' THEN CURRENT_TIMESTAMP(3) ELSE refunded_time END
            WHERE id = #{id} AND order_status = #{fromStatus}
            """)
    int transitionBenefitOrder(@Param("id") Long id, @Param("fromStatus") String fromStatus,
                               @Param("toStatus") String toStatus, @Param("reserveEntryId") Long reserveEntryId,
                               @Param("refundEntryId") Long refundEntryId,
                               @Param("deliveryReference") String deliveryReference,
                               @Param("operatorUid") Long operatorUid, @Param("reason") String reason);

    @Insert("""
            INSERT INTO t_virtual_benefit_order_history(id, order_id, from_status, to_status, operator_uid, action_reason)
            VALUES (#{id}, #{orderId}, #{fromStatus}, #{toStatus}, #{operatorUid}, #{reason})
            """)
    int insertOrderHistory(@Param("id") Long id, @Param("orderId") Long orderId,
                           @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus,
                           @Param("operatorUid") Long operatorUid, @Param("reason") String reason);

    @Select("""
            SELECT * FROM t_virtual_benefit_order WHERE user_id = #{userId}
            ORDER BY create_time DESC, id DESC LIMIT #{offset}, #{limit}
            """)
    List<BenefitOrderPO> selectUserOrders(@Param("userId") Long userId, @Param("offset") int offset,
                                         @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM t_virtual_benefit_order WHERE user_id = #{userId}")
    long countUserOrders(@Param("userId") Long userId);

    @Select("""
            SELECT * FROM t_virtual_benefit_order
            WHERE (#{status} IS NULL OR order_status = #{status})
            ORDER BY update_time DESC, id DESC LIMIT #{offset}, #{limit}
            """)
    List<BenefitOrderPO> selectAdminOrders(@Param("status") String status, @Param("offset") int offset,
                                          @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM t_virtual_benefit_order WHERE (#{status} IS NULL OR order_status = #{status})")
    long countAdminOrders(@Param("status") String status);

    @Insert("""
            INSERT IGNORE INTO t_thank_ticket_daily(id, user_id, ticket_date, granted_count, used_count, version)
            VALUES (#{id}, #{userId}, #{date}, #{granted}, 0, 0)
            """)
    int insertThankTicketIfAbsent(@Param("id") Long id, @Param("userId") Long userId,
                                  @Param("date") LocalDate date, @Param("granted") int granted);

    @Select("""
            SELECT * FROM t_thank_ticket_daily
            WHERE user_id = #{userId} AND ticket_date = #{date} LIMIT 1
            """)
    ThankTicketPO selectThankTicket(@Param("userId") Long userId, @Param("date") LocalDate date);

    @Update("""
            UPDATE t_thank_ticket_daily SET used_count = used_count + 1, version = version + 1
            WHERE user_id = #{userId} AND ticket_date = #{date} AND used_count < granted_count
            """)
    int consumeThankTicket(@Param("userId") Long userId, @Param("date") LocalDate date);

    @Insert("""
            INSERT INTO t_thank_action(id, sender_uid, receiver_uid, target_type, target_id, thank_date, note)
            VALUES (#{id}, #{senderUid}, #{receiverUid}, #{targetType}, #{targetId}, #{date}, #{note})
            """)
    int insertThankAction(@Param("id") Long id, @Param("senderUid") Long senderUid,
                          @Param("receiverUid") Long receiverUid, @Param("targetType") String targetType,
                          @Param("targetId") String targetId, @Param("date") LocalDate date,
                          @Param("note") String note);

    @Select("""
            SELECT id, sender_uid AS senderUid, receiver_uid AS receiverUid, target_type AS targetType,
                   target_id AS targetId, thank_date AS thankDate, note, create_time AS createTime
            FROM t_thank_action WHERE sender_uid = #{userId}
            ORDER BY create_time DESC, id DESC LIMIT #{offset}, #{limit}
            """)
    List<Map<String, Object>> selectSentThanks(@Param("userId") Long userId, @Param("offset") int offset,
                                               @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM t_thank_action WHERE sender_uid = #{userId}")
    long countSentThanks(@Param("userId") Long userId);

    @Select("""
            SELECT a.id, a.sender_uid AS senderUid, a.receiver_uid AS receiverUid,
                   a.target_type AS targetType, a.target_id AS targetId, a.note, a.create_time AS createTime
            FROM t_thank_action a
            WHERE a.receiver_uid = #{userId}
            ORDER BY a.create_time DESC, a.id DESC LIMIT #{offset}, #{limit}
            """)
    List<Map<String, Object>> selectReceivedThanks(@Param("userId") Long userId,
                                                   @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM t_thank_action WHERE receiver_uid = #{userId}")
    long countReceivedThanks(@Param("userId") Long userId);

    @Select("""
            SELECT target_type AS groupKey, COUNT(*) AS groupCount
            FROM t_thank_action WHERE receiver_uid = #{userId}
            GROUP BY target_type
            """)
    List<Map<String, Object>> selectReceivedThankTypeStats(@Param("userId") Long userId);

    @Select("""
            SELECT COALESCE(
                CASE
                    WHEN a.target_type = 'POST' THEN
                        CASE ep.domain WHEN 1 THEN 'TECH' WHEN 2 THEN 'CAREER' WHEN 3 THEN 'READING'
                            WHEN 4 THEN 'LIFESTYLE' WHEN 5 THEN 'INVESTMENT' ELSE 'UNKNOWN' END
                    WHEN a.target_type = 'COMMENT' THEN
                        CASE ec.domain WHEN 1 THEN 'TECH' WHEN 2 THEN 'CAREER' WHEN 3 THEN 'READING'
                            WHEN 4 THEN 'LIFESTYLE' WHEN 5 THEN 'INVESTMENT' ELSE 'UNKNOWN' END
                    ELSE 'UNKNOWN'
                END, 'UNKNOWN'
            ) AS groupKey, COUNT(*) AS groupCount
            FROM t_thank_action a
            LEFT JOIN t_post_extension ep
              ON a.target_type = 'POST' AND ep.post_id = CAST(a.target_id AS UNSIGNED)
            LEFT JOIN t_int_comment c
              ON a.target_type = 'COMMENT' AND c.id = CAST(a.target_id AS UNSIGNED)
            LEFT JOIN t_post_extension ec ON ec.post_id = c.post_id
            WHERE a.receiver_uid = #{userId}
            GROUP BY groupKey
            """)
    List<Map<String, Object>> selectReceivedThankDomainStats(@Param("userId") Long userId);

    @Select("""
            <script>
            <choose>
              <when test="targetType == 'POST'">
                SELECT p.author_id
                FROM t_post_main p
                LEFT JOIN t_post_extension e ON e.post_id = p.id
                WHERE p.id = #{targetId} AND p.visibility = 1 AND p.post_status = 1
                  AND p.is_deleted = 0 AND p.author_id > 0
                  AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.anonymous')), 'false') = 'false'
                LIMIT 1
              </when>
              <when test="targetType == 'COMMENT'">
                SELECT c.author_id
                FROM t_int_comment c
                JOIN t_post_main p ON p.id = c.post_id
                LEFT JOIN t_post_extension e ON e.post_id = p.id
                WHERE c.id = #{targetId} AND c.comment_status = 1 AND c.is_deleted = 0
                  AND c.author_id > 0 AND p.visibility = 1 AND p.post_status = 1 AND p.is_deleted = 0
                  AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.anonymous')), 'false') = 'false'
                LIMIT 1
              </when>
              <otherwise>SELECT NULL</otherwise>
            </choose>
            </script>
            """)
    Long selectPublicThankTargetAuthor(@Param("targetType") String targetType, @Param("targetId") Long targetId);

    @Select("""
            SELECT p.author_id
            FROM t_post_main p
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            WHERE p.id = #{postId} AND p.visibility = 1 AND p.post_status = 1
              AND p.is_deleted = 0 AND p.author_id > 0
              AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.anonymous')), 'false') = 'false'
            LIMIT 1
            """)
    Long selectPublicPostAuthor(@Param("postId") Long postId);

    @Select("""
            SELECT CASE e.domain
                WHEN 1 THEN 'TECH' WHEN 2 THEN 'CAREER' WHEN 3 THEN 'READING'
                WHEN 4 THEN 'LIFESTYLE' WHEN 5 THEN 'INVESTMENT' ELSE NULL END
            FROM t_post_main p
            JOIN t_post_extension e ON e.post_id = p.id
            WHERE p.id = #{postId} LIMIT 1
            """)
    String selectPostDomainCode(@Param("postId") Long postId);

    @Select("""
            SELECT COUNT(*)
            FROM t_post_main p
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            WHERE p.author_id = #{authorId}
              AND p.visibility = 1
              AND p.post_status = 1
              AND p.is_deleted = 0
              AND p.author_id > 0
              AND CHAR_LENGTH(TRIM(p.title)) >= 4
              AND CHAR_LENGTH(TRIM(p.content)) >= 200
              AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.anonymous')), 'false') = 'false'
            """)
    long countQualifiedPublicPostsByAuthor(@Param("authorId") Long authorId);

    @Select("""
            SELECT COUNT(*)
            FROM t_post_main p
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            WHERE p.id = #{postId}
              AND p.author_id = #{authorId}
              AND p.visibility = 1
              AND p.post_status = 1
              AND p.is_deleted = 0
              AND p.author_id > 0
              AND CHAR_LENGTH(TRIM(p.title)) >= 4
              AND CHAR_LENGTH(TRIM(p.content)) >= 200
              AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.anonymous')), 'false') = 'false'
            """)
    int countQualifiedPublicPost(@Param("postId") Long postId, @Param("authorId") Long authorId);

    @Select("""
            SELECT CASE WHEN MIN(p.id) = #{postId} THEN 1 ELSE 0 END
            FROM t_post_main p
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            WHERE p.author_id = #{authorId}
              AND p.visibility = 1
              AND p.post_status = 1
              AND p.is_deleted = 0
              AND p.author_id > 0
              AND CHAR_LENGTH(TRIM(p.title)) >= 4
              AND CHAR_LENGTH(TRIM(p.content)) >= 200
              AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.anonymous')), 'false') = 'false'
            """)
    int isFirstQualifiedPublicPost(@Param("postId") Long postId, @Param("authorId") Long authorId);

    @Select("""
            SELECT COUNT(*) FROM t_incentive_domain_policy
            WHERE domain_code = #{domain} AND incentive_enabled = 1
            """)
    int countIncentiveEnabledDomain(@Param("domain") String domain);

    @Select("""
            SELECT COUNT(*) FROM t_incentive_domain_policy
            WHERE domain_code = #{domain} AND incentive_enabled = 1 AND bounty_enabled = 1
            """)
    int countBountyEnabledDomain(@Param("domain") String domain);

    @Insert("""
            INSERT INTO t_quota_bounty(
                id, title, description, domain_code, request_type, risk_category, quota, awarded_count, point_reward,
                total_budget, budget_period, reserved_budget, consumed_budget,
                bounty_status, created_by, updated_by, action_reason
            ) VALUES (
                #{id}, #{title}, #{description}, #{domainCode}, #{requestType}, #{riskCategory},
                #{quota}, #{awardedCount}, #{pointReward},
                #{totalBudget}, #{budgetPeriod}, #{reservedBudget}, #{consumedBudget},
                #{bountyStatus}, #{createdBy}, #{updatedBy}, #{actionReason}
            )
            """)
    int insertBounty(BountyPO po);

    @Select("SELECT * FROM t_quota_bounty WHERE id = #{id}")
    BountyPO selectBounty(@Param("id") Long id);

    @Select("SELECT * FROM t_quota_bounty WHERE id = #{id} FOR UPDATE")
    BountyPO lockBounty(@Param("id") Long id);

    @Update("""
            UPDATE t_quota_bounty SET bounty_status = #{status}, updated_by = #{operatorUid}, action_reason = #{reason}
            WHERE id = #{id} AND bounty_status = #{expectedStatus}
            """)
    int updateBountyStatus(@Param("id") Long id, @Param("expectedStatus") String expectedStatus,
                           @Param("status") String status, @Param("operatorUid") Long operatorUid,
                           @Param("reason") String reason);

    @Update("""
            UPDATE t_quota_bounty
            SET bounty_status = 'OPEN', budget_period = #{period}, reserved_budget = total_budget,
                updated_by = #{operatorUid}, action_reason = #{reason}
            WHERE id = #{id} AND bounty_status = 'DRAFT' AND budget_period IS NULL
            """)
    int openBounty(@Param("id") Long id, @Param("period") String period,
                   @Param("operatorUid") Long operatorUid, @Param("reason") String reason);

    @Update("""
            UPDATE t_quota_bounty
            SET awarded_count = awarded_count + 1,
                consumed_budget = consumed_budget + point_reward
            WHERE id = #{id} AND bounty_status = 'OPEN' AND awarded_count < quota
              AND consumed_budget + point_reward <= reserved_budget
            """)
    int consumeBountyQuota(@Param("id") Long id);

    @Update("""
            UPDATE t_quota_bounty
            SET awarded_count = awarded_count + 1,
                consumed_budget = consumed_budget + point_reward
            WHERE id = #{id} AND bounty_status IN ('OPEN', 'CLOSED')
              AND awarded_count < quota AND consumed_budget + point_reward <= total_budget
            """)
    int consumeBountyQuotaForAppeal(@Param("id") Long id);

    @Insert("""
            INSERT IGNORE INTO t_bounty_platform_budget_guard(
                period_key, budget_limit, reserved_amount, awarded_amount, version
            ) VALUES (#{period}, #{budgetLimit}, 0, 0, 0)
            """)
    int insertPlatformBountyBudgetIfAbsent(@Param("period") String period,
                                           @Param("budgetLimit") long budgetLimit);

    @Update("""
            UPDATE t_bounty_platform_budget_guard
            SET reserved_amount = reserved_amount + #{amount}, version = version + 1
            WHERE period_key = #{period}
              AND reserved_amount + awarded_amount + #{amount} <= budget_limit
            """)
    int reservePlatformBountyBudget(@Param("period") String period, @Param("amount") long amount);

    @Update("""
            UPDATE t_bounty_platform_budget_guard
            SET reserved_amount = reserved_amount - #{amount}, version = version + 1
            WHERE period_key = #{period} AND reserved_amount >= #{amount}
            """)
    int releasePlatformBountyBudget(@Param("period") String period, @Param("amount") long amount);

    @Update("""
            UPDATE t_bounty_platform_budget_guard
            SET reserved_amount = reserved_amount - #{amount},
                awarded_amount = awarded_amount + #{amount},
                version = version + 1
            WHERE period_key = #{period} AND reserved_amount >= #{amount}
              AND awarded_amount + #{amount} <= budget_limit
            """)
    int consumePlatformBountyBudget(@Param("period") String period, @Param("amount") long amount);

    @Insert("""
            INSERT IGNORE INTO t_bounty_user_budget_guard(
                period_key, user_id, awarded_amount, budget_limit, version
            ) VALUES (#{period}, #{userId}, 0, #{budgetLimit}, 0)
            """)
    int insertUserBountyBudgetIfAbsent(@Param("period") String period, @Param("userId") Long userId,
                                       @Param("budgetLimit") long budgetLimit);

    @Update("""
            UPDATE t_bounty_user_budget_guard
            SET awarded_amount = awarded_amount + #{amount}, version = version + 1
            WHERE period_key = #{period} AND user_id = #{userId}
              AND awarded_amount + #{amount} <= budget_limit
            """)
    int consumeUserBountyBudget(@Param("period") String period, @Param("userId") Long userId,
                                @Param("amount") long amount);

    @Select("""
            SELECT * FROM t_quota_bounty
            WHERE bounty_status = 'OPEN'
            ORDER BY update_time DESC, id DESC LIMIT #{offset}, #{limit}
            """)
    List<BountyPO> selectOpenBounties(@Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM t_quota_bounty WHERE bounty_status = 'OPEN'")
    long countOpenBounties();

    @Insert("""
            INSERT INTO t_quota_bounty_submission(
                id, bounty_id, applicant_uid, public_post_id, request_type, risk_category,
                evidence, submission_status
            ) VALUES (
                #{id}, #{bountyId}, #{applicantUid}, #{publicPostId}, #{requestType}, #{riskCategory},
                #{evidence}, #{submissionStatus}
            )
            """)
    int insertBountySubmission(BountySubmissionPO po);

    @Select("SELECT * FROM t_quota_bounty_submission WHERE id = #{id} FOR UPDATE")
    BountySubmissionPO lockBountySubmission(@Param("id") Long id);

    @Update("""
            UPDATE t_quota_bounty_submission
            SET submission_status = #{status}, reviewer_uid = #{reviewerUid},
                review_reason = #{reason}, reward_entry_id = #{entryId}
            WHERE id = #{id} AND submission_status = 'SUBMITTED'
            """)
    int reviewBountySubmission(@Param("id") Long id, @Param("status") String status,
                               @Param("reviewerUid") Long reviewerUid, @Param("reason") String reason,
                               @Param("entryId") Long entryId);

    @Insert("""
            INSERT INTO t_quota_bounty_appeal(
                id, submission_id, applicant_uid, original_status, original_reviewer_uid,
                appeal_reason, appeal_status
            ) VALUES (
                #{id}, #{submissionId}, #{applicantUid}, #{originalStatus}, #{originalReviewerUid},
                #{appealReason}, #{appealStatus}
            )
            """)
    int insertBountyAppeal(BountyAppealPO po);

    @Select("SELECT * FROM t_quota_bounty_appeal WHERE id = #{id}")
    BountyAppealPO selectBountyAppeal(@Param("id") Long id);

    @Select("SELECT * FROM t_quota_bounty_appeal WHERE id = #{id} FOR UPDATE")
    BountyAppealPO lockBountyAppeal(@Param("id") Long id);

    @Select("""
            SELECT * FROM t_quota_bounty_appeal
            WHERE applicant_uid = #{userId}
            ORDER BY create_time DESC, id DESC LIMIT #{offset}, #{limit}
            """)
    List<BountyAppealPO> selectUserBountyAppeals(@Param("userId") Long userId,
                                                 @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM t_quota_bounty_appeal WHERE applicant_uid = #{userId}")
    long countUserBountyAppeals(@Param("userId") Long userId);

    @Select("""
            SELECT * FROM t_quota_bounty_appeal
            WHERE (#{status} IS NULL OR appeal_status = #{status})
            ORDER BY update_time DESC, id DESC LIMIT #{offset}, #{limit}
            """)
    List<BountyAppealPO> selectBountyAppealQueue(@Param("status") String status,
                                                 @Param("offset") int offset, @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*) FROM t_quota_bounty_appeal
            WHERE (#{status} IS NULL OR appeal_status = #{status})
            """)
    long countBountyAppealQueue(@Param("status") String status);

    @Update("""
            UPDATE t_quota_bounty_appeal
            SET appeal_status = #{status}, reviewer_uid = #{reviewerUid},
                review_reason = #{reason}, compensation_entry_id = #{entryId}
            WHERE id = #{id} AND appeal_status = 'SUBMITTED'
            """)
    int reviewBountyAppeal(@Param("id") Long id, @Param("status") String status,
                           @Param("reviewerUid") Long reviewerUid, @Param("reason") String reason,
                           @Param("entryId") Long entryId);

    @Update("""
            UPDATE t_quota_bounty
            SET awarded_count = awarded_count - 1,
                consumed_budget = consumed_budget - #{recoveredAmount}
            WHERE id = #{id} AND awarded_count > 0 AND consumed_budget >= #{recoveredAmount}
            """)
    int restoreBountyQuota(@Param("id") Long id, @Param("recoveredAmount") long recoveredAmount);

    @Update("""
            UPDATE t_bounty_platform_budget_guard
            SET awarded_amount = awarded_amount - #{amount},
                reserved_amount = reserved_amount + CASE WHEN #{reserveAgain} = 1 THEN #{amount} ELSE 0 END,
                version = version + 1
            WHERE period_key = #{period} AND awarded_amount >= #{amount}
            """)
    int rollbackPlatformBountyAward(@Param("period") String period, @Param("amount") long amount,
                                    @Param("reserveAgain") int reserveAgain);

    @Update("""
            UPDATE t_bounty_user_budget_guard
            SET awarded_amount = awarded_amount - #{amount}, version = version + 1
            WHERE period_key = #{period} AND user_id = #{userId} AND awarded_amount >= #{amount}
            """)
    int rollbackUserBountyAward(@Param("period") String period, @Param("userId") Long userId,
                                @Param("amount") long amount);

    @Update("""
            UPDATE t_quota_bounty_submission
            SET submission_status = #{status}, reviewer_uid = #{reviewerUid},
                review_reason = #{reason}, reward_entry_id = #{entryId}
            WHERE id = #{id} AND submission_status = #{expectedStatus}
            """)
    int resolveBountySubmissionAppeal(@Param("id") Long id, @Param("expectedStatus") String expectedStatus,
                                      @Param("status") String status, @Param("reviewerUid") Long reviewerUid,
                                      @Param("reason") String reason, @Param("entryId") Long entryId);

    @Select("""
            SELECT * FROM t_quota_bounty_submission WHERE applicant_uid = #{userId}
            ORDER BY create_time DESC, id DESC LIMIT #{offset}, #{limit}
            """)
    List<BountySubmissionPO> selectUserBountySubmissions(@Param("userId") Long userId,
                                                         @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM t_quota_bounty_submission WHERE applicant_uid = #{userId}")
    long countUserBountySubmissions(@Param("userId") Long userId);

    @Select("""
            SELECT * FROM t_quota_bounty_submission
            WHERE (#{status} IS NULL OR submission_status = #{status})
            ORDER BY update_time DESC, id DESC LIMIT #{offset}, #{limit}
            """)
    List<BountySubmissionPO> selectBountySubmissionQueue(@Param("status") String status,
                                                         @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM t_quota_bounty_submission WHERE (#{status} IS NULL OR submission_status = #{status})")
    long countBountySubmissionQueue(@Param("status") String status);

    @Insert("""
            INSERT INTO t_community_role_definition(
                id, role_code, role_name, description, domain_code, min_account_age_days,
                min_domain_reputation, min_activity_count, max_violation_count,
                min_curation_accuracy_bps, requires_no_risk_freeze, enabled,
                created_by, updated_by, action_reason
            ) VALUES (
                #{id}, #{roleCode}, #{roleName}, #{description}, #{domainCode}, #{minAccountAgeDays},
                #{minDomainReputation}, #{minActivityCount}, #{maxViolationCount},
                #{minCurationAccuracyBps}, #{requiresNoRiskFreeze}, #{enabled},
                #{createdBy}, #{updatedBy}, #{actionReason}
            )
            ON DUPLICATE KEY UPDATE
                role_name = VALUES(role_name), description = VALUES(description),
                min_account_age_days = VALUES(min_account_age_days),
                min_domain_reputation = VALUES(min_domain_reputation),
                min_activity_count = VALUES(min_activity_count),
                max_violation_count = VALUES(max_violation_count),
                min_curation_accuracy_bps = VALUES(min_curation_accuracy_bps),
                requires_no_risk_freeze = VALUES(requires_no_risk_freeze),
                enabled = VALUES(enabled), updated_by = VALUES(updated_by), action_reason = VALUES(action_reason)
            """)
    int upsertRoleDefinition(RoleDefinitionPO po);

    @Select("""
            SELECT * FROM t_community_role_definition
            WHERE role_code = #{roleCode} AND domain_code = #{domainCode} LIMIT 1
            """)
    RoleDefinitionPO selectRoleDefinition(@Param("roleCode") String roleCode,
                                          @Param("domainCode") String domainCode);

    @Select("""
            SELECT * FROM t_community_role_definition WHERE enabled = 1
            ORDER BY domain_code, role_code LIMIT #{limit}
            """)
    List<RoleDefinitionPO> selectRoleDefinitions(@Param("limit") int limit);

    @Insert("""
            INSERT INTO t_community_role_metric(
                user_id, domain_code, curation_correct_count, curation_reviewed_count, updated_by, update_reason
            ) VALUES (#{userId}, #{domainCode}, #{correct}, #{reviewed}, #{operatorUid}, #{reason})
            ON DUPLICATE KEY UPDATE
                curation_correct_count = VALUES(curation_correct_count),
                curation_reviewed_count = VALUES(curation_reviewed_count),
                updated_by = VALUES(updated_by), update_reason = VALUES(update_reason)
            """)
    int upsertRoleMetric(@Param("userId") Long userId, @Param("domainCode") String domainCode,
                         @Param("correct") int correct, @Param("reviewed") int reviewed,
                         @Param("operatorUid") Long operatorUid, @Param("reason") String reason);

    @Select("""
            SELECT
                COALESCE(DATEDIFF(CURRENT_DATE, ua.create_time), 0) AS accountAgeDays,
                COALESCE(ra.total_balance, 0) AS domainReputation,
                (SELECT COUNT(*) FROM t_incentive_ledger il
                 WHERE il.user_id = #{userId}
                   AND il.account_type = 'REPUTATION'
                   AND il.domain_code = #{domainCode}
                   AND il.entry_type = 'REWARD'
                   AND il.create_time >= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 180 DAY)
                   AND il.rule_code IN (
                       'FIRST_QUALIFIED_POST_REPUTATION_V1',
                       'ACCEPTED_ANSWER_REPUTATION_V1',
                       'SUGGESTION_ACCEPTED_REPUTATION_V1',
                       'FRESHNESS_UPDATED_REPUTATION_V1',
                       'HELPFUL_COMMENT_REPUTATION_V1',
                       'OPERATION_SELECTED_REPUTATION_V1',
                       'REPORT_ACTION_TAKEN_REPUTATION_V1',
                       'COLLAB_ACCEPTED_REPUTATION_V1'
                   )) AS activityCount,
                (SELECT COUNT(*) FROM t_moderation_keyword_hit mh
                 WHERE mh.uid = #{userId} AND mh.action = 'BLOCK' AND mh.review_status IS NULL) AS violationCount,
                CASE WHEN COALESCE(rm.curation_reviewed_count, 0) = 0 THEN 0
                     ELSE FLOOR(rm.curation_correct_count * 10000 / rm.curation_reviewed_count) END AS curationAccuracyBps,
                CASE WHEN EXISTS (
                    SELECT 1 FROM t_incentive_account fa
                    WHERE fa.user_id = #{userId} AND fa.account_status = 'FROZEN'
                ) OR EXISTS (
                    SELECT 1 FROM t_incentive_freeze_record fr
                    WHERE fr.user_id = #{userId} AND fr.freeze_status = 'ACTIVE'
                ) THEN 1 ELSE 0 END AS riskFrozen
            FROM t_user_account ua
            LEFT JOIN t_incentive_account ra
              ON ra.user_id = ua.id AND ra.account_type = 'REPUTATION' AND ra.domain_code = #{domainCode}
            LEFT JOIN t_community_role_metric rm
              ON rm.user_id = ua.id AND rm.domain_code = #{domainCode}
            WHERE ua.id = #{userId} AND ua.is_deleted = 0
            LIMIT 1
            """)
    Map<String, Object> selectRoleMetrics(@Param("userId") Long userId, @Param("domainCode") String domainCode);

    @Insert("""
            INSERT INTO t_community_role_application(
                id, applicant_uid, role_code, domain_code, statement,
                eligibility_snapshot_json, application_status
            ) VALUES (
                #{id}, #{applicantUid}, #{roleCode}, #{domainCode}, #{statement},
                #{eligibilitySnapshotJson}, #{applicationStatus}
            )
            """)
    int insertRoleApplication(RoleApplicationPO po);

    @Select("SELECT * FROM t_community_role_application WHERE id = #{id} FOR UPDATE")
    RoleApplicationPO lockRoleApplication(@Param("id") Long id);

    @Select("SELECT * FROM t_community_role_application WHERE id = #{id}")
    RoleApplicationPO selectRoleApplication(@Param("id") Long id);

    @Update("""
            UPDATE t_community_role_application
            SET application_status = #{status}, reviewer_uid = #{reviewerUid}, review_reason = #{reason}
            WHERE id = #{id} AND application_status = 'SUBMITTED'
            """)
    int reviewRoleApplication(@Param("id") Long id, @Param("status") String status,
                              @Param("reviewerUid") Long reviewerUid, @Param("reason") String reason);

    @Select("""
            SELECT * FROM t_community_role_application WHERE applicant_uid = #{userId}
            ORDER BY create_time DESC, id DESC LIMIT #{offset}, #{limit}
            """)
    List<RoleApplicationPO> selectUserRoleApplications(@Param("userId") Long userId,
                                                       @Param("offset") int offset, @Param("limit") int limit);

    @Select("""
            SELECT * FROM t_community_role_application
            WHERE applicant_uid = #{userId}
              AND role_code = #{roleCode}
              AND domain_code = #{domainCode}
            ORDER BY create_time DESC, id DESC
            LIMIT 1
            """)
    RoleApplicationPO selectLatestUserRoleApplication(@Param("userId") Long userId,
                                                      @Param("roleCode") String roleCode,
                                                      @Param("domainCode") String domainCode);

    @Select("""
            WITH ranked AS (
                SELECT application.*,
                       ROW_NUMBER() OVER (
                           PARTITION BY role_code, domain_code
                           ORDER BY create_time DESC, id DESC
                       ) AS row_num
                FROM t_community_role_application application
                WHERE applicant_uid = #{userId}
            )
            SELECT * FROM ranked
            WHERE row_num = 1
            ORDER BY update_time DESC, id DESC
            LIMIT #{limit}
            """)
    List<RoleApplicationPO> selectLatestUserRoleApplications(@Param("userId") Long userId,
                                                             @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM t_community_role_application WHERE applicant_uid = #{userId}")
    long countUserRoleApplications(@Param("userId") Long userId);

    @Select("""
            SELECT * FROM t_community_role_application
            WHERE (#{status} IS NULL OR application_status = #{status})
            ORDER BY update_time DESC, id DESC LIMIT #{offset}, #{limit}
            """)
    List<RoleApplicationPO> selectRoleApplicationQueue(@Param("status") String status,
                                                       @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM t_community_role_application WHERE (#{status} IS NULL OR application_status = #{status})")
    long countRoleApplicationQueue(@Param("status") String status);

    @Insert("""
            INSERT INTO t_community_role_grant(
                id, user_id, role_code, domain_code, grant_status, application_id,
                granted_by, grant_reason, granted_at, expires_at
            ) VALUES (
                #{id}, #{userId}, #{roleCode}, #{domainCode}, #{grantStatus}, #{applicationId},
                #{grantedBy}, #{grantReason}, #{grantedAt}, #{expiresAt}
            )
            """)
    int insertRoleGrant(RoleGrantPO po);

    @Select("SELECT * FROM t_community_role_grant WHERE id = #{id} FOR UPDATE")
    RoleGrantPO lockRoleGrant(@Param("id") Long id);

    @Update("""
            UPDATE t_community_role_grant
            SET grant_status = #{toStatus}, action_by = #{operatorUid}, action_reason = #{reason},
                expires_at = COALESCE(#{expiresAt}, expires_at)
            WHERE id = #{id} AND grant_status = #{fromStatus}
            """)
    int transitionRoleGrant(@Param("id") Long id, @Param("fromStatus") String fromStatus,
                            @Param("toStatus") String toStatus, @Param("operatorUid") Long operatorUid,
                            @Param("reason") String reason, @Param("expiresAt") LocalDateTime expiresAt);

    @Insert("""
            INSERT INTO t_community_role_grant_history(
                id, grant_id, from_status, to_status, operator_uid, action_reason
            ) VALUES (#{id}, #{grantId}, #{fromStatus}, #{toStatus}, #{operatorUid}, #{reason})
            """)
    int insertRoleGrantHistory(@Param("id") Long id, @Param("grantId") Long grantId,
                               @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus,
                               @Param("operatorUid") Long operatorUid, @Param("reason") String reason);

    @Select("""
            SELECT * FROM t_community_role_grant WHERE user_id = #{userId}
            ORDER BY update_time DESC, id DESC LIMIT #{offset}, #{limit}
            """)
    List<RoleGrantPO> selectUserRoleGrants(@Param("userId") Long userId,
                                           @Param("offset") int offset, @Param("limit") int limit);

    @Select("""
            SELECT * FROM t_community_role_grant
            WHERE user_id = #{userId}
              AND role_code = #{roleCode}
              AND domain_code = #{domainCode}
            ORDER BY update_time DESC, id DESC
            LIMIT 1
            """)
    RoleGrantPO selectLatestUserRoleGrant(@Param("userId") Long userId,
                                          @Param("roleCode") String roleCode,
                                          @Param("domainCode") String domainCode);

    @Select("""
            WITH ranked AS (
                SELECT grant_row.*,
                       ROW_NUMBER() OVER (
                           PARTITION BY role_code, domain_code
                           ORDER BY update_time DESC, id DESC
                       ) AS row_num
                FROM t_community_role_grant grant_row
                WHERE user_id = #{userId}
            )
            SELECT * FROM ranked
            WHERE row_num = 1
            ORDER BY update_time DESC, id DESC
            LIMIT #{limit}
            """)
    List<RoleGrantPO> selectLatestUserRoleGrants(@Param("userId") Long userId,
                                                 @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM t_community_role_grant WHERE user_id = #{userId}")
    long countUserRoleGrants(@Param("userId") Long userId);

    @Select("""
            SELECT * FROM t_community_role_grant
            WHERE (#{status} IS NULL OR grant_status = #{status})
            ORDER BY update_time DESC, id DESC LIMIT #{offset}, #{limit}
            """)
    List<RoleGrantPO> selectRoleGrantQueue(@Param("status") String status,
                                           @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM t_community_role_grant WHERE (#{status} IS NULL OR grant_status = #{status})")
    long countRoleGrantQueue(@Param("status") String status);

    @Select("""
            SELECT id FROM t_community_role_grant
            WHERE grant_status IN ('ACTIVE', 'SUSPENDED') AND expires_at IS NOT NULL
              AND expires_at <= CURRENT_TIMESTAMP(3)
            ORDER BY expires_at, id LIMIT #{limit}
            """)
    List<Long> selectExpiredGrantIds(@Param("limit") int limit);

    @Select("""
            SELECT h.id,
                   h.grant_id AS grantId,
                   h.from_status AS fromStatus,
                   h.to_status AS toStatus,
                   h.operator_uid AS operatorUid,
                   h.action_reason AS actionReason,
                   h.create_time AS createTime
            FROM t_community_role_grant_history h
            WHERE h.grant_id = #{grantId}
            ORDER BY h.create_time DESC, h.id DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> selectRoleGrantHistory(@Param("grantId") Long grantId,
                                                     @Param("limit") int limit);

    @Select("""
            SELECT
                (SELECT COUNT(*)
                 FROM t_incentive_ledger l
                 WHERE l.user_id = #{userId}
                   AND l.entry_type = 'REWARD'
                   AND l.create_time >= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 180 DAY)
                   AND l.rule_code IN (
                       'FIRST_QUALIFIED_POST_REPUTATION_V1',
                       'ACCEPTED_ANSWER_REPUTATION_V1',
                       'SUGGESTION_ACCEPTED_REPUTATION_V1',
                       'FRESHNESS_UPDATED_REPUTATION_V1',
                       'HELPFUL_COMMENT_REPUTATION_V1',
                       'OPERATION_SELECTED_REPUTATION_V1',
                       'REPORT_ACTION_TAKEN_REPUTATION_V1',
                       'COLLAB_ACCEPTED_REPUTATION_V1'
                   )) AS recentTrustedContributionCount,
                (SELECT COUNT(*)
                 FROM t_collab_content_maintenance_task task
                 WHERE task.assignee_uid = #{userId}
                   AND task.domain = #{domain}
                   AND task.task_status = 'COMPLETED') AS completedMaintenanceTaskCount,
                (SELECT COUNT(*)
                 FROM t_collab_content_maintenance_task task
                 WHERE task.assignee_uid = #{userId}
                   AND task.domain = #{domain}
                   AND task.task_status = 'CLAIMED'
                   AND task.reviewed_at IS NOT NULL) AS returnedMaintenanceTaskCount
            """)
    Map<String, Object> selectRoleReviewContributionSummary(@Param("userId") Long userId,
                                                            @Param("domain") Integer domain);
}
