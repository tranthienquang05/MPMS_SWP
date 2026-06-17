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

    @Value("${openai.edit.url:https://api.openai.com/v1/images/edits}")
    private String openaiEditUrl;

    @Value("${openai.image.model}")
    private String openaiImageModel;

    // ── OpenAI Vision ───────────────────────────────────────────────────

    @Value("${openai.vision.model}")
    private String openaiVisionModel;

    @Value("${openai.vision.url}")
    private String openaiVisionUrl;

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

    @Bean(name = "openaiEditUrl")
    public String openaiEditUrl() {
        return openaiEditUrl;
    }

    // ── Beans: OpenAI Vision ────────────────────────────────────────────

    @Bean(name = "openaiVisionModel")
    public String openaiVisionModel() {
        return openaiVisionModel;
    }

    @Bean(name = "openaiVisionUrl")
    public String openaiVisionUrl() {
        return openaiVisionUrl;
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private RestTemplate buildBearerRestTemplate(String apiKey) {
        RestTemplate restTemplate = new RestTemplate();
        ClientHttpRequestInterceptor authInterceptor = (request, body, execution) -> {
            request.getHeaders().set("Authorization", "Bearer " + apiKey);
            // NOTE: Do NOT set Content-Type here.
            // JSON calls set it via HttpEntity; multipart calls let Spring set
            // the correct boundary automatically.
            return execution.execute(request, body);
        };
        restTemplate.setInterceptors(List.of(authInterceptor));
        return restTemplate;
    }
}
