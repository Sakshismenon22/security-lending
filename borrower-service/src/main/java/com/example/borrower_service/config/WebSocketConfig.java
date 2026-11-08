package com.example.borrower_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final String frontend;
    public WebSocketConfig(@Value("${borrower.frontend-origin:http://localhost:5173}") String frontend) { this.frontend=frontend; }
    @Override public void configureMessageBroker(MessageBrokerRegistry r) { r.enableSimpleBroker("/topic"); }
    @Override public void registerStompEndpoints(StompEndpointRegistry r) { r.addEndpoint("/ws").setAllowedOrigins(frontend); }
}
