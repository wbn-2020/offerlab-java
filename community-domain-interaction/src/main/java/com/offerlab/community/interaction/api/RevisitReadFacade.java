package com.offerlab.community.interaction.api;

import com.offerlab.community.interaction.api.dto.RevisitReadStateDTO;

import java.util.Collection;
import java.util.Map;

/**
 * Read-only projection used by update digests.
 *
 * Reading this facade never refreshes, completes, snoozes, ignores or marks a
 * revisit item as read.
 */
public interface RevisitReadFacade {

    Map<String, RevisitReadStateDTO> findVisibleStates(Long uid, Collection<String> resourceKeys);
}
