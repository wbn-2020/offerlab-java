package com.offerlab.community.post.application;

interface ContentAssistAiClient {

    boolean enabled();

    boolean configured();

    Completion complete(ContentAssistScene scene, ContentAssistPrompt prompt) throws Exception;

    default long maximumCompletionDurationMillis() {
        return 15_000L;
    }

    record Completion(String provider,
                      String contentJson,
                      int promptTokens,
                      int completionTokens,
                      long estimatedCostMicros) {
    }
}
