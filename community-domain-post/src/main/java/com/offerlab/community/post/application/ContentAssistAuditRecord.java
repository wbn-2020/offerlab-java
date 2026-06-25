package com.offerlab.community.post.application;

record ContentAssistAuditRecord(Long uid,
                                String scene,
                                String provider,
                                String status,
                                Integer domain,
                                int contentLength,
                                String contentHash,
                                int promptTokens,
                                int completionTokens,
                                long estimatedCostMicros,
                                String errorCode) {
}
