package com.example.lender_service.config;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.*;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableScheduling
public class ApplicationConfig implements WebMvcConfigurer {
  private final String origin;

  public ApplicationConfig(
      @Value("${lender.frontend-origin:http://localhost:5174}") String origin) {
    this.origin = origin;
  }

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  public JsonMapper jsonMapper() {
    return JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
  }

  @Bean
  public RestClient marketDataClient(
      @Value("${lender.market-data-url:http://localhost:8080}") String url) {
    var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(3000);
    factory.setReadTimeout(5000);
    return RestClient.builder().baseUrl(url).requestFactory(factory).build();
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/api/**")
        .allowedOrigins(origin)
        .allowedMethods("GET", "POST", "PATCH", "OPTIONS")
        .allowedHeaders("Content-Type", "Idempotency-Key");
  }
}
