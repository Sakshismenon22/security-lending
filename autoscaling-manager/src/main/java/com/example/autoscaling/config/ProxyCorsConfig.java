package com.example.autoscaling.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Allows the local BorrowDesk frontend to call the Borrower reverse-proxy endpoint. */
@Configuration
public class ProxyCorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public ProxyCorsConfig(
            @Value("${autoscaling.proxy.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String origins) {
        this.allowedOrigins = origins.split(",");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/borrower/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
