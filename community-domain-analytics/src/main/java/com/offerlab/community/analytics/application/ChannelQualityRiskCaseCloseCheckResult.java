package com.offerlab.community.analytics.application;

public record ChannelQualityRiskCaseCloseCheckResult(
        String requirementLevel,
        String result,
        String reasonCode,
        String summary) {
}
