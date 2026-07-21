package com.offerlab.community.incentive.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.incentive.api.IncentiveDtos.RewardInboxReconcileDTO;
import com.offerlab.community.incentive.api.IncentiveProjectionReconciliationFacade;
import com.offerlab.community.incentive.infrastructure.IncentiveMapper;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.AccountPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.LedgerPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.RewardInboxPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.RewardRulePO;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.security.AdminPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RewardInboxReconcileTest {

    @Mock
    private IncentiveMapper mapper;
    @Mock
    private AdminPermissionService permissions;
    @Mock
    private AdminAuditService audit;

    private AccountLedgerService service;

    @BeforeEach
    void setUp() {
        service = new AccountLedgerService(
                mapper,
                new SnowflakeIdGenerator(1, 1),
                permissions,
                audit,
                new ObjectMapper());
    }

    @Test
    void previewCountsOnlyBoundedPendingRewardsPastSla() {
        when(mapper.countOverduePendingInbox(any(LocalDateTime.class), eq(11))).thenReturn(11);

        LocalDateTime before = LocalDateTime.now();
        int due = service.countOverduePendingRewards(10, 8L);
        LocalDateTime after = LocalDateTime.now();

        assertEquals(11, due);
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).countOverduePendingInbox(cutoff.capture(), eq(11));
        assertFalse(cutoff.getValue().isBefore(before.minusMinutes(
                IncentiveProjectionReconciliationFacade.REWARD_INBOX_SLA_MINUTES).minusSeconds(1)));
        assertFalse(cutoff.getValue().isAfter(after.minusMinutes(
                IncentiveProjectionReconciliationFacade.REWARD_INBOX_SLA_MINUTES).plusSeconds(1)));
    }

    @Test
    void reconcileUsesExistingInboxProcessingAndStableLedgerKey() {
        RewardInboxPO inbox = new RewardInboxPO();
        inbox.setId(101L);
        inbox.setStableKey("stable-101");
        inbox.setRecipientUid(22L);
        inbox.setDomainCode("GLOBAL");
        inbox.setRuleCode("TRUSTED_REWARD_V1");
        inbox.setRuleVersion(1);
        inbox.setInboxStatus("PENDING");

        RewardRulePO rule = new RewardRulePO();
        rule.setRuleCode("TRUSTED_REWARD_V1");
        rule.setRuleVersion(1);
        rule.setAccountType("POINT");
        rule.setDomainCode("GLOBAL");
        rule.setRewardAmount(5L);
        rule.setDailyUserCap(0L);
        rule.setLifetimeUserCap(0L);
        rule.setEnabled(1);

        AccountPO account = new AccountPO();
        account.setId(501L);
        account.setUserId(22L);
        account.setAccountType("POINT");
        account.setDomainCode("GLOBAL");
        account.setTotalBalance(0L);
        account.setAvailableBalance(0L);
        account.setFrozenBalance(0L);
        account.setAccountStatus("ACTIVE");
        account.setVersion(0L);

        AtomicReference<LedgerPO> insertedLedger = new AtomicReference<>();
        when(mapper.selectOverduePendingInboxIdsForUpdate(any(LocalDateTime.class), eq(1)))
                .thenReturn(List.of(101L));
        when(mapper.lockInbox(101L)).thenReturn(inbox);
        when(mapper.selectRewardRule("TRUSTED_REWARD_V1", 1)).thenReturn(rule);
        when(mapper.lockRewardGuard(22L, "TRUSTED_REWARD_V1", 1))
                .thenReturn(Map.of(
                        "counterDate", LocalDate.now(),
                        "dailyAwarded", 0L,
                        "lifetimeAwarded", 0L));
        when(mapper.lockAccount(22L, "POINT", "GLOBAL")).thenReturn(account);
        when(mapper.updateAccount(501L, 0L, 5L, 5L, 0L, "ACTIVE")).thenReturn(1);
        when(mapper.insertLedger(any(LedgerPO.class))).thenAnswer(invocation -> {
            insertedLedger.set(invocation.getArgument(0));
            return 1;
        });
        when(mapper.selectLedgerById(anyLong())).thenAnswer(invocation -> insertedLedger.get());
        when(mapper.lockPendingRecoveryDebts(501L, 100)).thenReturn(List.of());
        when(mapper.sumPendingRecoveryDebt(501L)).thenReturn(0L);
        when(mapper.countActiveBlockingFreezes(501L)).thenReturn(0);
        when(mapper.updateAccountRecoveryState(501L, 0L, "ACTIVE")).thenReturn(1);
        when(mapper.incrementRewardGuard(22L, "TRUSTED_REWARD_V1", 1, 5L)).thenReturn(1);
        when(mapper.updateInbox(eq(101L), eq("APPLIED"), isNull(), anyLong(), isNull()))
                .thenReturn(1);
        when(mapper.countOverduePendingInbox(any(LocalDateTime.class), eq(1))).thenReturn(0);

        RewardInboxReconcileDTO result =
                service.reconcileOverduePendingRewards(1, "process overdue reward", 8L);

        assertEquals(1, result.getProcessedCount());
        assertEquals(1, result.getAppliedCount());
        assertEquals(0, result.getRejectedCount());
        assertTrue(result.getCoverageComplete());
        assertEquals("LEDGER:REWARD:INBOX:stable-101",
                insertedLedger.get().getIdempotencyKey());
        assertEquals("REWARD_INBOX", insertedLedger.get().getReferenceType());
        assertEquals("101", insertedLedger.get().getReferenceId());
        verify(mapper).selectOverduePendingInboxIdsForUpdate(any(LocalDateTime.class), eq(1));
        verify(mapper).updateInbox(eq(101L), eq("APPLIED"), isNull(), anyLong(), isNull());
    }

    @Test
    void reconcileDoesNotSelectAppliedHistoryAndRemainsGloballyBounded() {
        when(mapper.selectOverduePendingInboxIdsForUpdate(any(LocalDateTime.class), eq(2)))
                .thenReturn(List.of());
        when(mapper.countOverduePendingInbox(any(LocalDateTime.class), eq(1))).thenReturn(0);

        RewardInboxReconcileDTO result =
                service.reconcileOverduePendingRewards(2, "bounded pending-only repair", 8L);

        assertEquals(0, result.getProcessedCount());
        assertTrue(result.getCoverageComplete());
        verify(mapper).selectOverduePendingInboxIdsForUpdate(any(LocalDateTime.class), eq(2));
    }

    @Test
    void implementationKeepsPendingOnlyStableKeyAndNoSchedulerBoundaries() throws Exception {
        String serviceSource = Files.readString(Path.of(
                "src/main/java/com/offerlab/community/incentive/application/AccountLedgerService.java"),
                StandardCharsets.UTF_8);
        String mapperSource = Files.readString(Path.of(
                "src/main/java/com/offerlab/community/incentive/infrastructure/IncentiveMapper.java"),
                StandardCharsets.UTF_8);
        int queryStart = mapperSource.indexOf("selectOverduePendingInboxIdsForUpdate");
        int querySqlStart = mapperSource.lastIndexOf("@Select", queryStart);
        String overdueQuery = mapperSource.substring(querySqlStart, queryStart);

        assertTrue(overdueQuery.contains("inbox_status = 'PENDING'"));
        assertFalse(overdueQuery.contains("APPLIED"));
        assertTrue(serviceSource.contains("processInbox(id, null)"));
        assertTrue(serviceSource.contains("\"INBOX:\" + inbox.getStableKey()"));
        assertFalse(serviceSource.contains("@Scheduled"));
    }
}
