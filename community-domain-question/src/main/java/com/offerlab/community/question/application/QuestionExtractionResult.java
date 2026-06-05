package com.offerlab.community.question.application;

import java.util.List;

public record QuestionExtractionResult(
        List<ExtractedQuestion> questions,
        String provider,
        boolean fallbackUsed,
        int promptTokens,
        int completionTokens,
        long estimatedCostMicros,
        String errorCode
) {
    public static QuestionExtractionResult rules(List<ExtractedQuestion> questions) {
        return new QuestionExtractionResult(questions == null ? List.of() : questions,
                "rules", false, 0, 0, 0L, null);
    }

    public static QuestionExtractionResult none(String errorCode) {
        return new QuestionExtractionResult(List.of(), "none", false, 0, 0, 0L, errorCode);
    }

    public QuestionExtractionResult withQuestions(List<ExtractedQuestion> normalizedQuestions) {
        return new QuestionExtractionResult(normalizedQuestions == null ? List.of() : normalizedQuestions,
                provider, fallbackUsed, promptTokens, completionTokens, estimatedCostMicros, errorCode);
    }
}
