package com.example.borrower_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  @Value("${lendora.auth-enabled:true}")
  private boolean authEnabled;

  private final String frontend;

  public WebSocketConfig(
    @Value("${borrower.frontend-origin:http://localhost:5173}") String frontend
  ) {
    this.frontend = frontend;
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry r) {
    r.enableSimpleBroker("/topic");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry r) {
    var endpoint = r.addEndpoint("/ws").setAllowedOrigins(frontend);
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
