package com.example.manga_management.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import com.example.manga_management.ai.dto.AIRequestDTO;
import com.example.manga_management.ai.dto.AIResponseDTO;
import com.example.manga_management.ai.enums.AIFeature;

@Service
public class AISupportService {

    private static final Logger log = LoggerFactory.getLogger(AISupportService.class);

    private final OpenAIService openAIService;
    private final String openaiApiKey;

    public AISupportService(
            OpenAIService openAIService,
            @Qualifier("openaiApiKey") String openaiApiKey) {
        this.openAIService = openAIService;
        this.openaiApiKey = openaiApiKey;
    }

    /**
     * Execute an AI feature based on the incoming request.
     */
    public AIResponseDTO runFeature(AIRequestDTO request) {
        try {
            // 1. Resolve the feature enum
            AIFeature feature = AIFeature.fromCode(request.getFeature());

            // 2. Build the final prompt
            String finalPrompt = feature.getBasePrompt();
            if (request.getPrompt() != null && !request.getPrompt().isBlank()) {
                finalPrompt += ". " + request.getPrompt();
            }

            // 3. Dispatch based on feature type
            if ("image".equals(feature.getType())) {
                return handleImageFeature(request.getImageBase64(), finalPrompt);
            }

            if ("vision".equals(feature.getType())) {
                // Check if key is configured
                if (!isKeyConfigured(openaiApiKey, "your-openai-key")) {
                    log.warn("OpenAI API key not configured for vision");
                    return AIResponseDTO.builder()
                            .status("error")
                            .message("Dịch vụ phân tích ảnh chưa được cấu hình API key.")
                            .build();
                }

                if (request.getImageBase64() == null || request.getImageBase64().isBlank()) {
                    return AIResponseDTO.builder()
                            .status("error")
                            .message("Tính năng này yêu cầu hình ảnh đầu vào (base64)")
                            .build();
                }
                OpenAIService.AIResult result = openAIService.analyzeCanvas(
                        request.getImageBase64(), finalPrompt);
                return AIResponseDTO.builder()
                        .status("success")
                        .type("text")
                        .result(result.content())
                        .requestId(result.requestId())
                        .build();
            }

            return AIResponseDTO.builder()
                    .status("error")
                    .message("Unsupported feature type: " + feature.getType())
                    .build();

        } catch (HttpClientErrorException ex) {
            log.error("AI API returned client error [status={}]", ex.getStatusCode(), ex);
            String body = ex.getResponseBodyAsString();
            return AIResponseDTO.builder()
                    .status("error")
                    .message(mapHttpClientError(body))
                    .build();

        } catch (Exception ex) {
            log.error("AI feature execution failed", ex);
            return AIResponseDTO.builder()
                    .status("error")
                    .message(ex.getMessage())
                    .build();
        }
    }

    // ── image feature ──────────────────────────────────────

    /**
     * If imageBase64 is provided, use gpt-image-1 /images/edits to edit the
     * actual canvas drawing. Otherwise fall back to text-only generation.
     */
    private AIResponseDTO handleImageFeature(String imageBase64, String prompt) {
        if (!isKeyConfigured(openaiApiKey, "your-openai-key")) {
            log.warn("OpenAI API key not configured");
            return AIResponseDTO.builder()
                    .status("error")
                    .message("Dịch vụ tạo ảnh chưa được cấu hình API key.")
                    .build();
        }

        try {
            OpenAIService.AIResult result;
            if (imageBase64 != null && !imageBase64.isBlank()) {
                // Canvas image present → edit it with gpt-image-1
                log.info("Using gpt-image-1 /images/edits with canvas input");
                result = openAIService.editImage(imageBase64, prompt);
            } else {
                // No canvas → generate from text prompt only
                log.info("No canvas input, falling back to text-only generation");
                result = openAIService.generateImage(prompt);
            }
            return AIResponseDTO.builder()
                    .status("success")
                    .type("image_base64")
                    .result(result.content())
                    .requestId(result.requestId())
                    .build();
        } catch (Exception ex) {
            log.error("Image feature failed: {}", ex.getMessage(), ex);
            return AIResponseDTO.builder()
                    .status("error")
                    .message("Xử lý ảnh thất bại: " + ex.getMessage())
                    .build();
        }
    }

    /**
     * @deprecated Kept for reference only. Use handleImageFeature() instead.
     */
    @SuppressWarnings("unused")
    private AIResponseDTO handleImageGeneration(String prompt) {
        if (!isKeyConfigured(openaiApiKey, "your-openai-key")) {
            log.warn("OpenAI API key not configured");
            return AIResponseDTO.builder()
                    .status("error")
                    .message("Dịch vụ tạo ảnh chưa được cấu hình API key.")
                    .build();
        }

        try {
            OpenAIService.AIResult result = openAIService.generateImage(prompt);
            return AIResponseDTO.builder()
                    .status("success")
                    .type("image_base64")
                    .result(result.content())
                    .requestId(result.requestId())
                    .build();
        } catch (Exception ex) {
            log.error("OpenAI DALL-E failed: {}", ex.getMessage(), ex);
            return AIResponseDTO.builder()
                    .status("error")
                    .message("Tạo ảnh thất bại: " + ex.getMessage())
                    .build();
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private boolean isKeyConfigured(String key, String placeholder) {
        return key != null && !key.isBlank() && !key.equalsIgnoreCase(placeholder);
    }

    private String mapHttpClientError(String responseBody) {
        if (responseBody == null) {
            return "Lỗi không xác định từ API";
        }
        if (responseBody.contains("insufficient_quota") || responseBody.contains("quota_exceeded")) {
            return "Hết quota, vui lòng liên hệ admin";
        }
        if (responseBody.contains("rate_limit_exceeded")) {
            return "Quá nhiều request, thử lại sau";
        }
        if (responseBody.contains("model_not_allowed")) {
            return "Model không được phép, vui lòng liên hệ admin";
        }
        if (responseBody.contains("purchase_quota_temporarily_reserved")) {
            return "Quota tạm thời không khả dụng, thử lại sau ít phút";
        }
        return "Lỗi API: " + responseBody;
    }
}
