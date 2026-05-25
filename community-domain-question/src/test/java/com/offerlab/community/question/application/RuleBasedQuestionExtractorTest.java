package com.offerlab.community.question.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.api.dto.TagDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RuleBasedQuestionExtractorTest {

    private final RuleBasedQuestionExtractor extractor = new RuleBasedQuestionExtractor(new ObjectMapper());

    @Test
    void extractsQuestionsAndCarriesPostMetadata() {
        PostDTO post = PostDTO.builder()
                .id(100L)
                .content("""
                        1. How does Redis persistence work?
                        2. Explain JVM memory model?
                        This line is not a question.
                        """)
                .extJson("{\"company\":\"ByteDance\",\"position\":\"Backend\",\"round\":\"Technical\"}")
                .tags(List.of(TagDTO.builder().id(10L).name("Redis").build()))
                .build();

        List<ExtractedQuestion> result = extractor.extract(post);

        assertFalse(result.isEmpty());
        assertEquals("ByteDance", result.get(0).getCompany());
        assertEquals("Backend", result.get(0).getPosition());
        assertEquals("Technical", result.get(0).getInterviewRound());
        assertEquals(List.of(10L), result.get(0).getTagIds());
    }
}
