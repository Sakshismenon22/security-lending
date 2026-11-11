package com.example.lifecycle_service.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {
  @Bean
  Clock lifecycleClock() {
    return Clock.systemUTC();
  }
}
