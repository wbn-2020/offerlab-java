package com.offerlab.community.post.api;

import com.offerlab.community.post.collaboration.api.ContentMaintenanceTaskDTO;

import java.time.LocalDateTime;

/**
 * Write boundary exposed to other domains. Analytics must call this boundary
 * for channel-health dispatches instead of reaching into collaboration
 * persistence or application internals.
 */
public interface ContentMaintenanceTaskCommandFacade {

    ContentMaintenanceTaskDTO dispatchChannelHealthTask(
            ContentMaintenanceTaskBatchDispatchCmd cmd,
            Long operatorUid);

    ContentMaintenanceBatchTaskCoordinationResult extendBatchActiveTaskDueAt(
            Long batchId,
            Integer domain,
            LocalDateTime effectiveDueAt,
            Long operatorUid);

    ContentMaintenanceBatchTaskCoordinationResult reassignBatchActiveTasks(
            Long batchId,
            Integer domain,
            Long replacementUid,
            Long operatorUid);

    ContentMaintenanceBatchTaskCoordinationResult withdrawBatchOpenTasks(
            Long batchId,
            Integer domain,
            Integer expectedOpenTaskCount,
            Integer expectedActiveTaskCount,
            Long operatorUid,
            String note);
}
