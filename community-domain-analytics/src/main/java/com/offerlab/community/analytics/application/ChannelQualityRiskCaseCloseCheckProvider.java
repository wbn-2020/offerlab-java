package com.offerlab.community.analytics.application;

public interface ChannelQualityRiskCaseCloseCheckProvider {
    String code();

    int contractVersion();

    int order();

    ChannelQualityRiskCaseCloseCheckResult evaluate(ChannelQualityRiskCaseCloseCheckContext context);
}
