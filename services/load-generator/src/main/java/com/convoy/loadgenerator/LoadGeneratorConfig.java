package com.convoy.loadgenerator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class LoadGeneratorConfig {

    @Bean
    WebClient ingestionWebClient(WebClient.Builder builder, @Value("${app.target.url}") String targetUrl) {
        // Use Spring Boot's auto-configured builder, not the static WebClient.builder()
        // factory, so the WebClient inherits the app's Jackson config (snake_case naming) -
        // otherwise it serializes with default camelCase field names.
        return builder.baseUrl(targetUrl).build();
    }
}
