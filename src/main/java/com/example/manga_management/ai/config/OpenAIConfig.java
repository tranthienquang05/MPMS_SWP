package com.example.manga_management.ai.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

@Configuration
public class OpenAIConfig {

    // ── OpenAI ──────────────────────────────────────────────────────────

    @Value("${openai.api.key}")
    private String openaiApiKey;

    @Value("${openai.api.url}")
    private String openaiUrl;

    @Value("${openai.image.model}")
    private String openaiImageModel;

    // ── Google Gemini ───────────────────────────────────────────────────

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.api.url}")
    private String geminiUrl;

    // ── Beans: OpenAI ───────────────────────────────────────────────────

    @Bean(name = "openaiRestTemplate")
    public RestTemplate openaiRestTemplate() {
        return buildBearerRestTemplate(openaiApiKey);
    }

    @Bean(name = "openaiApiKey")
    public String openaiApiKey() {
        return openaiApiKey;
    }

    @Bean(name = "openaiImageModel")
    public String openaiImageModel() {
        return openaiImageModel;
    }

    @Bean(name = "openaiUrl")
    public String openaiUrl() {
        return openaiUrl;
    }

    // ── Beans: Gemini ───────────────────────────────────────────────────

    @Bean(name = "geminiApiKey")
    public String geminiApiKey() {
        return geminiApiKey;
    }

    @Bean(name = "geminiUrl")
    public String geminiUrl() {
        return geminiUrl;
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private RestTemplate buildBearerRestTemplate(String apiKey) {
        RestTemplate restTemplate = new RestTemplate();
        ClientHttpRequestInterceptor authInterceptor = (request, body, execution) -> {
            request.getHeaders().set("Authorization", "Bearer " + apiKey);
            request.getHeaders().set("Content-Type", "application/json");
            return execution.execute(request, body);
        };
        restTemplate.setInterceptors(List.of(authInterceptor));
        return restTemplate;
    }
}
