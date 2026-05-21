package com.offerlab.community.infra.es.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.es.client.ElasticsearchHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(ElasticsearchProperties.class)
public class ElasticsearchConfig {

    @Bean
    public HttpClient elasticsearchJavaHttpClient(ElasticsearchProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .build();
    }

    @Bean
    public ElasticsearchHttpClient elasticsearchHttpClient(HttpClient elasticsearchJavaHttpClient,
                                                           ObjectMapper objectMapper,
                                                           ElasticsearchProperties properties) {
        return new ElasticsearchHttpClient(elasticsearchJavaHttpClient, objectMapper, properties);
    }
}
