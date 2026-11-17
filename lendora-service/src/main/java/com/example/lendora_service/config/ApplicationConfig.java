package com.example.lendora_service.config;

import jakarta.servlet.http.*;
import java.time.Clock;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class ApplicationConfig implements WebMvcConfigurer {

  private final String[] origins;

  public ApplicationConfig(
    @Value("${lendora.allowed-origins}") String origins
  ) {
    this.origins = Arrays.stream(origins.split(","))
      .map(String::trim)
      .filter(s -> !s.isBlank())
      .toArray(String[]::new);
  }

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
      .addMapping("/api/**")
      .allowedOrigins(origins)
      .allowedMethods("GET", "POST", "OPTIONS")
      .allowedHeaders("Content-Type", "Accept")
      .allowCredentials(true);
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
      .addInterceptor(
        new HandlerInterceptor() {
          @Override
          public boolean preHandle(
            HttpServletRequest req,
            HttpServletResponse res,
            Object handler
          ) {
            res.setHeader("Cache-Control", "no-store");
            String origin = req.getHeader("Origin");
            if (
              !Set.of("GET", "HEAD", "OPTIONS").contains(req.getMethod()) &&
              origin != null &&
              !Arrays.asList(origins).contains(origin)
            ) throw new ResponseStatusException(
              HttpStatus.FORBIDDEN,
              "Request origin is not allowed."
            );
            return true;
          }
        }
      )
      .addPathPatterns("/api/**");
  }
}
