package com.offerlab.community.question.application;

import com.offerlab.community.post.api.dto.PostDTO;

import java.util.List;

public interface QuestionExtractor {
    List<ExtractedQuestion> extract(PostDTO post);
}
