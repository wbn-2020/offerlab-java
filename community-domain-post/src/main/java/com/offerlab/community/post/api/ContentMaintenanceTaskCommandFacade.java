package com.offerlab.community.post.api;

import com.offerlab.community.post.collaboration.api.ContentMaintenanceTaskDTO;

/**
 * Write boundary exposed to other domains. Analytics must call this boundary
 * for channel-health dispatches instead of reaching into collaboration
 * persistence or application internals.
 */
public interface ContentMaintenanceTaskCommandFacade {

    ContentMaintenanceTaskDTO dispatchChannelHealthTask(
            ContentMaintenanceTaskBatchDispatchCmd cmd,
            Long operatorUid);
}
