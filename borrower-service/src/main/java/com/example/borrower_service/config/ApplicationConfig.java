package com.example.borrower_service.config;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.*;
import java.time.Clock;

@Configuration
@EnableScheduling
public class ApplicationConfig implements WebMvcConfigurer {
    private final String frontend;
    public ApplicationConfig(@Value("${borrower.frontend-origin:http://localhost:5173}") String frontend) { this.frontend=frontend; }
    @Bean public Clock clock() { return Clock.systemUTC(); }
    @Bean public RestClient marketDataClient(@Value("${borrower.market-data-url:http://localhost:8080}") String url) {
        var factory=new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000); factory.setReadTimeout(5000);
        return RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }
    @Override public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET","POST","PUT","PATCH","DELETE","OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
