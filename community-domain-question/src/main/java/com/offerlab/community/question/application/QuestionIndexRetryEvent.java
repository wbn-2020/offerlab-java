package com.offerlab.community.question.application;

public record QuestionIndexRetryEvent(Long questionId, String operation, Throwable cause) {
}
