package com.securitylending.gateway.filter;

import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Ensures that every request has a safe correlation ID.
 * The same ID is forwarded to downstream services and returned to the caller.
 */
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    public static final String HEADER_NAME = "X-Correlation-ID";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incomingId = exchange.getRequest().getHeaders().getFirst(HEADER_NAME);
        String correlationId = isSafe(incomingId) ? incomingId : UUID.randomUUID().toString();

        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .headers(headers -> headers.set(HEADER_NAME, correlationId))
                .build();

        exchange.getResponse().getHeaders().set(HEADER_NAME, correlationId);

        return chain.filter(exchange.mutate().request(request).build());
    }

    private boolean isSafe(String value) {
        return value != null && SAFE_ID.matcher(value).matches();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
