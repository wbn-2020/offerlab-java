package com.offerlab.community.notification.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

@Configuration(proxyBeanMethods = false)
@EnableWebSocket
@ConditionalOnProperty(prefix = "offerlab.realtime", name = "websocket-enabled", havingValue = "true")
public class NotificationWebSocketConfig implements WebSocketConfigurer {

    private static final String NOTIFICATION_ENDPOINT = "/ws/notifications";

    private final NotificationWebSocketHandler notificationWebSocketHandler;
    private final String allowedOrigins;

    public NotificationWebSocketConfig(NotificationWebSocketHandler notificationWebSocketHandler,
                                       @Value("${offerlab.web.cors.allowed-origins:http://localhost:*,http://127.0.0.1:*}")
                                       String allowedOrigins) {
        this.notificationWebSocketHandler = notificationWebSocketHandler;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notificationWebSocketHandler, NOTIFICATION_ENDPOINT)
                .addInterceptors(new NotificationHandshakeInterceptor())
                .setAllowedOriginPatterns(parseAllowedOrigins());
    }

    @Bean
    public NotificationRealtimeCapability notificationRealtimeCapability() {
        return new WebSocketNotificationRealtimeCapability();
    }

    private String[] parseAllowedOrigins() {
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }
}
