package com.offerlab.community.infra.es.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "offerlab.elasticsearch")
public class ElasticsearchProperties {
    private boolean enabled = true;
    private String url = "http://127.0.0.1:9200";
    private String postIndex = "post_idx";
    private String questionIndex = "question_idx";
    private int connectTimeoutMillis = 800;
    private int requestTimeoutMillis = 1500;
}
