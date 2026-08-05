package com.offerlab.community.analytics.application;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class AnalyticsTimeConfiguration {

    @Bean
    Clock analyticsClock() {
        return Clock.systemUTC();
    }
}
