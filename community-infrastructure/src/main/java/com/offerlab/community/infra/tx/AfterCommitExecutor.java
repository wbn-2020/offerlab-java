package com.offerlab.community.infra.tx;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
public class AfterCommitExecutor {

    public void execute(Runnable task, String description) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            run(task, description);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                run(task, description);
            }
        });
    }

    private void run(Runnable task, String description) {
        try {
            task.run();
        } catch (Exception e) {
            log.warn("after-commit task failed: {}", description, e);
        }
    }
}