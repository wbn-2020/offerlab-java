package com.offerlab.community.post.api;

import com.offerlab.community.post.api.dto.KnowledgeMaintenanceSourceDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface KnowledgeMaintenanceReadFacade {

    int SOURCE_ORDER_REFERENCE = 4;
    int SOURCE_ORDER_RELATION_PROPOSAL = 5;
    int SOURCE_ORDER_RELATION_REVIEW = 6;
    int SOURCE_ORDER_MAINTENANCE_TASK = 7;

    List<KnowledgeMaintenanceSourceDTO> listActions(Long uid, int limit);

    List<KnowledgeMaintenanceSourceDTO> listActions(Long uid,
                                                    String actionType,
                                                    String status,
                                                    LocalDateTime cursorTime,
                                                    Integer cursorSourceOrder,
                                                    Long cursorSourceId,
                                                    int limit);

    long countActions(Long uid, String actionType, String status);

    Map<String, Long> countActionsByType(Long uid);
}
