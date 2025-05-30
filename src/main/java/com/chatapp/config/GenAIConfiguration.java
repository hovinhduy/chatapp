package com.chatapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.genai.Client;

@Configuration
public class GenAIConfiguration {

    @Bean
    public Client genaiClient(@Value("${gemini.api.key}") String apiKey) {
        return Client.builder()
                .apiKey(apiKey)
                .build();
    }
}
