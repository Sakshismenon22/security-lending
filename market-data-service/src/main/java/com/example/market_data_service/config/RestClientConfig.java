package com.example.market_data_service.config;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import javax.net.ssl.SSLContext;
import org.apache.hc.core5.ssl.SSLContexts;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient yahooRestClient() throws Exception {
        TrustStrategy trustAllCertificates =
                (certificateChain, authenticationType) -> true;

        SSLContext sslContext = SSLContexts.custom()
                .loadTrustMaterial(null, trustAllCertificates)
                .build();

        SSLConnectionSocketFactory socketFactory =
                SSLConnectionSocketFactoryBuilder.create()
                        .setSslContext(sslContext)
                        .setHostnameVerifier(
                                NoopHostnameVerifier.INSTANCE
                        )
                        .build();

        PoolingHttpClientConnectionManager connectionManager =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setSSLSocketFactory(socketFactory)
                        .build();

        HttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .build();

        HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory(
                        httpClient
                );

        requestFactory.setConnectionRequestTimeout(10_000);
        requestFactory.setConnectionRequestTimeout(10_000);
        return RestClient.builder()
                .baseUrl("https://query2.finance.yahoo.com")
                .defaultHeader(
                "User-Agent",
                "Mozilla/5.0")
                .requestFactory(requestFactory)
                .build();
    }
}
