package com.example.manga_management.ai.service;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OpenAIService {

    private static final Logger log = LoggerFactory.getLogger(OpenAIService.class);

    private final RestTemplate openaiRestTemplate;
    private final String openaiImageModel;
    private final String openaiUrl;
    private final String openaiEditUrl;
    private final String openaiVisionModel;
    private final String openaiVisionUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAIService(
            @Qualifier("openaiRestTemplate") RestTemplate openaiRestTemplate,
            @Qualifier("openaiImageModel") String openaiImageModel,
            @Qualifier("openaiUrl") String openaiUrl,
            @Qualifier("openaiEditUrl") String openaiEditUrl,
            @Qualifier("openaiVisionModel") String openaiVisionModel,
            @Qualifier("openaiVisionUrl") String openaiVisionUrl) {
        this.openaiRestTemplate = openaiRestTemplate;
        this.openaiImageModel = openaiImageModel;
        this.openaiUrl = openaiUrl;
        this.openaiEditUrl = openaiEditUrl;
        this.openaiVisionModel = openaiVisionModel;
        this.openaiVisionUrl = openaiVisionUrl;
    }

    /**
     * Result holder that pairs the API output with the x-request-id header.
     */
    public record AIResult(String content, String requestId) {}

    // ── OpenAI gpt-image-1 image edit (canvas → AI) ──────────────────────

    /**
     * Edit an existing canvas image via OpenAI /images/edits (gpt-image-1).
     * Sends the canvas PNG as multipart/form-data together with the prompt.
     * Returns the edited image as a base64-encoded string.
     *
     * @param imageBase64 raw base64 string of the canvas PNG (no data: prefix)
     * @param prompt      instruction for the edit
     */
    public AIResult editImage(String imageBase64, String prompt) throws Exception {
        byte[] imageBytes = Base64.getDecoder().decode(
                imageBase64.startsWith("data:") ? imageBase64.substring(imageBase64.indexOf(",") + 1) : imageBase64
        );

        // Wrap raw bytes as a named file resource so Spring sends it as a file part
        ByteArrayResource imageResource = new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() { return "canvas.png"; }
        };

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("model", openaiImageModel);
        parts.add("prompt", prompt);
        parts.add("n", "1");
        parts.add("size", "1024x1024");
        parts.add("response_format", "b64_json");
        parts.add("image", imageResource);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        // Bearer token is added by the RestTemplate interceptor
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(parts, headers);

        log.debug("OpenAI image edit request → {} (prompt length={})", openaiEditUrl, prompt.length());

        ResponseEntity<String> response = openaiRestTemplate.postForEntity(openaiEditUrl, requestEntity, String.class);
        String requestId = extractRequestId(response);
        String responseBody = response.getBody();

        log.debug("OpenAI edit raw response [requestId={}]: {}", requestId, responseBody);

        if (responseBody == null || responseBody.isBlank()) {
            throw new RuntimeException("OpenAI returned an empty response [requestId=" + requestId + "]");
        }

        JsonNode root = objectMapper.readTree(responseBody);
        String resultB64 = root.path("data").get(0).path("b64_json").asText();

        log.info("gpt-image-1 edit completed [requestId={}]", requestId);
        return new AIResult(resultB64, requestId);
    }

    // ── OpenAI gpt-image-1 text-only generation (fallback) ───────────────

    /**
     * Generate a brand-new image from a text prompt only (no input canvas).
     * Used as fallback when no imageBase64 is provided.
     */
    public AIResult generateImage(String prompt) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", openaiImageModel);
        body.put("prompt", prompt);
        body.put("size", "1024x1024");
        body.put("output_format", "png");

        log.debug("OpenAI image generation request body: {}", body);

        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, jsonHeaders);

        ResponseEntity<String> response = openaiRestTemplate.postForEntity(openaiUrl, requestEntity, String.class);
        String requestId = extractRequestId(response);
        String responseBody = response.getBody();

        log.debug("OpenAI raw response [requestId={}]: {}", requestId, responseBody);

        if (responseBody == null || responseBody.isBlank()) {
            throw new RuntimeException("OpenAI returned an empty response body [requestId=" + requestId + "]");
        }

        JsonNode root = objectMapper.readTree(responseBody);
        String imageBase64Result = root.path("data").get(0).path("b64_json").asText();

        log.info("gpt-image-1 text-only generation completed [requestId={}]", requestId);
        return new AIResult(imageBase64Result, requestId);
    }

    // ── OpenAI gpt-4o vision / analysis ──────────────────────────────────

    /**
     * Analyse a canvas image using OpenAI gpt-4o vision model.
     */
    public AIResult analyzeCanvas(String imageBase64, String instruction) throws Exception {
        String rawBase64 = imageBase64.startsWith("data:")
                ? imageBase64.substring(imageBase64.indexOf(",") + 1)
                : imageBase64;

        Map<String, Object> textContent = new LinkedHashMap<>();
        textContent.put("type", "text");
        textContent.put("text", instruction);

        Map<String, Object> imageUrlInner = new LinkedHashMap<>();
        imageUrlInner.put("url", "data:image/png;base64," + rawBase64);

        Map<String, Object> imageContent = new LinkedHashMap<>();
        imageContent.put("type", "image_url");
        imageContent.put("image_url", imageUrlInner);

        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", List.of(textContent, imageContent));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", openaiVisionModel);
        body.put("messages", List.of(userMessage));
        body.put("max_tokens", 1000);

        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, jsonHeaders);

        ResponseEntity<String> response = openaiRestTemplate.postForEntity(openaiVisionUrl, requestEntity, String.class);
        String requestId = extractRequestId(response);

        JsonNode root = objectMapper.readTree(response.getBody());
        String text = root.path("choices").get(0)
                .path("message").path("content").asText();

        log.info("OpenAI gpt-4o vision analysis completed [requestId={}]", requestId);

        return new AIResult(text, requestId);
    }

    // ── helper ──────────────────────────────────────────────────────────

    private String extractRequestId(ResponseEntity<?> response) {
        HttpHeaders headers = response.getHeaders();
        List<String> values = headers.get("x-request-id");
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }
}
