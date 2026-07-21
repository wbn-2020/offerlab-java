package com.offerlab.community.post.api;

import com.offerlab.community.post.api.dto.KnowledgeMaintenanceSourceDTO;

import java.util.List;

public interface KnowledgeMaintenanceReadFacade {

    List<KnowledgeMaintenanceSourceDTO> listActions(Long uid, int limit);
}
