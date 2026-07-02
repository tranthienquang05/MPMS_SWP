package com.example.manga_management.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MangaChatService {

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.model}")
    private String geminiModel;

    @Value("${openai.api.key}")
    private String openaiApiKey;

    @Value("${openai.image.model}")
    private String openaiImageModel;

    private final RestTemplate restTemplate = new RestTemplate();

    private final String SYSTEM_PROMPT = "Bạn là trợ lý AI chuyên hỗ trợ mangaka trong phần mềm "
            + "SANKYUU. Hỗ trợ kỹ thuật vẽ manga, nhận xét trang manga, "
            + "gợi ý cải thiện, hỗ trợ workflow deadline và task. "
            + "Khi user yêu cầu vẽ/tạo ảnh, trả lời ngắn gọn rằng "
            + "bạn đang tạo ảnh. Trả lời bằng tiếng Việt.";

    public String generateChatResponse(String prompt) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + geminiModel + ":generateContent?key=" + geminiApiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new HashMap<>();

        List<Map<String, Object>> contents = new ArrayList<>();

        // Add System prompt
        Map<String, Object> systemContent = new HashMap<>();
        systemContent.put("role", "user");
        List<Map<String, Object>> systemParts = new ArrayList<>();
        Map<String, Object> systemPart = new HashMap<>();
        systemPart.put("text", SYSTEM_PROMPT + "\n\n" + prompt);
        systemParts.add(systemPart);
        systemContent.put("parts", systemParts);

        contents.add(systemContent);
        requestBody.put("contents", contents);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.getBody().get("candidates");
                if (candidates != null && !candidates.isEmpty()) {
                    Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                    List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                    if (parts != null && !parts.isEmpty()) {
                        return (String) parts.get(0).get("text");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Xin lỗi, đã có lỗi xảy ra khi gọi Gemini API: " + e.getMessage();
        }
        return "Xin lỗi, không thể nhận phản hồi từ AI.";
    }

    private String translateToEnglish(String text) {
        try {
            return generateChatResponse(
                    "Translate to English for image generation. Return ONLY the translation, no explanation: " + text
            ).trim();
        } catch (Exception e) {
            return text;
        }
    }

    public String generateImage(String prompt) {
        String url = "https://api.openai.com/v1/images/generations";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openaiApiKey);

        String englishPrompt = translateToEnglish(prompt);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", openaiImageModel); // gpt-image-2
        requestBody.put("prompt", englishPrompt);
        requestBody.put("n", 1);
        requestBody.put("size", "1024x1024");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
                if (data != null && !data.isEmpty()) {
                    // gpt-image-2 chỉ trả về b64_json, KHÔNG có url
                    String b64 = (String) data.get(0).get("b64_json");
                    return "data:image/png;base64," + b64;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public String editImage(MultipartFile image, String prompt) {
        String url = "https://api.openai.com/v1/images/edits";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(openaiApiKey);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        try {
            Resource imageResource = new ByteArrayResource(image.getBytes()) {
                @Override
                public String getFilename() {
                    return image.getOriginalFilename() != null ? image.getOriginalFilename() : "image.png";
                }
            };
            body.add("image", imageResource);
            body.add("prompt", prompt != null && !prompt.trim().isEmpty() ? prompt : "Edit this image");
            body.add("n", 1);
            body.add("size", "1024x1024");
            body.add("response_format", "b64_json");

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
                if (data != null && !data.isEmpty()) {
                    return (String) data.get(0).get("b64_json");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
