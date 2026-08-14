package com.convoy.loadgenerator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class LoadGeneratorConfig {

    @Bean
    WebClient ingestionWebClient(@Value("${app.target.url}") String targetUrl) {
        return WebClient.builder().baseUrl(targetUrl).build();
    }
}
