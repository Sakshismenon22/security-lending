package com.example.fix_adapter_service.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(AdapterProperties.class)
public class AdapterConfig {
    @Bean public Clock clock() {return Clock.systemUTC();}
    @Bean public JsonMapper jsonMapper() {return JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();}
}
