package com.example.manga_management.ai.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OpenAIService {

    private static final Logger log = LoggerFactory.getLogger(OpenAIService.class);

    private final RestTemplate openaiRestTemplate;
    private final String openaiImageModel;
    private final String openaiUrl;
    private final String geminiApiKey;
    private final String geminiUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAIService(
            @Qualifier("openaiRestTemplate") RestTemplate openaiRestTemplate,
            @Qualifier("openaiImageModel") String openaiImageModel,
            @Qualifier("openaiUrl") String openaiUrl,
            @Qualifier("geminiApiKey") String geminiApiKey,
            @Qualifier("geminiUrl") String geminiUrl) {
        this.openaiRestTemplate = openaiRestTemplate;
        this.openaiImageModel = openaiImageModel;
        this.openaiUrl = openaiUrl;
        this.geminiApiKey = geminiApiKey;
        this.geminiUrl = geminiUrl;
    }

    /**
     * Result holder that pairs the API output with the x-request-id header.
     */
    public record AIResult(String content, String requestId) {}

    // ── OpenAI DALL-E image generation ──────────────────────────────────

    /**
     * Generate an image via OpenAI DALL-E /images/generations endpoint.
     * Returns the generated image URL.
     */
    public AIResult generateImage(String prompt) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", openaiImageModel);
        body.put("prompt", prompt);
        body.put("n", 1);
        body.put("size", "1024x1024");

        ResponseEntity<String> response = openaiRestTemplate.postForEntity(openaiUrl, body, String.class);
        String requestId = extractRequestId(response);

        JsonNode root = objectMapper.readTree(response.getBody());
        String imageUrl = root.path("data").get(0).path("url").asText();

        log.info("OpenAI image generated successfully [requestId={}]", requestId);

        return new AIResult(imageUrl, requestId);
    }

    // ── Gemini vision / analysis ────────────────────────────────────────

    /**
     * Analyse a canvas image using Google Gemini vision model.
     */
    public AIResult analyzeCanvas(String imageBase64, String instruction) throws Exception {
        String url = geminiUrl + "?key=" + geminiApiKey;

        String rawBase64 = imageBase64.startsWith("data:")
                ? imageBase64.substring(imageBase64.indexOf(",") + 1)
                : imageBase64;

        Map<String, Object> textPart = Map.of("text", instruction);
        Map<String, Object> inlineData = Map.of(
                "inline_data", Map.of(
                        "mime_type", "image/png",
                        "data", rawBase64
                )
        );

        Map<String, Object> content = Map.of(
                "parts", List.of(textPart, inlineData)
        );

        Map<String, Object> body = Map.of(
                "contents", List.of(content)
        );

        RestTemplate geminiRestTemplate = new RestTemplate();
        ResponseEntity<String> response = geminiRestTemplate.postForEntity(url, body, String.class);

        JsonNode root = objectMapper.readTree(response.getBody());
        String text = root.path("candidates").get(0)
                .path("content").path("parts").get(0)
                .path("text").asText();

        log.info("Gemini analysis completed");

        return new AIResult(text, null);
    }

    // ── helper ──────────────────────────────────────────────────────────

    private String extractRequestId(ResponseEntity<?> response) {
        HttpHeaders headers = response.getHeaders();
        List<String> values = headers.get("x-request-id");
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }
}
