package com.example.lender_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  @Value("${lendora.auth-enabled:true}")
  private boolean authEnabled;

  private final String origin;

  public WebSocketConfig(
    @Value("${lender.frontend-origin:http://localhost:5174}") String origin
  ) {
    this.origin = origin;
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    var endpoint = registry.addEndpoint("/ws").setAllowedOrigins(origin);
    if (authEnabled) endpoint.addInterceptors(
      new org.springframework.web.socket.server.HandshakeInterceptor() {
        @Override
        public boolean beforeHandshake(
          org.springframework.http.server.ServerHttpRequest request,
          org.springframework.http.server.ServerHttpResponse response,
          org.springframework.web.socket.WebSocketHandler handler,
          java.util.Map<String, Object> attributes
        ) {
          response.setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
          return false;
        }

        @Override
        public void afterHandshake(
          org.springframework.http.server.ServerHttpRequest request,
          org.springframework.http.server.ServerHttpResponse response,
          org.springframework.web.socket.WebSocketHandler handler,
          Exception error
        ) {}
      }
    );
  }
}
