package com.contractguard.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * A single configured RestClient bean for talking to the LLM provider.
 *
 * Defining it once here rather than constructing a client inside the service
 * means timeouts and the base URL live in one place, and tests can inject a
 * different instance.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient llmRestClient(
            @Value("${contractguard.llm.base-url}") String baseUrl,
            @Value("${contractguard.llm.timeout-seconds}") int timeoutSeconds) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
