package com.offerlab.community.post.api;

import com.offerlab.community.post.api.dto.OperationCurationFeedbackDTO;

import java.util.List;

public interface CreatorCurationFeedbackFacade {

    List<OperationCurationFeedbackDTO> listCreatorCurationFeedback(Long authorUid, int limit);
}
