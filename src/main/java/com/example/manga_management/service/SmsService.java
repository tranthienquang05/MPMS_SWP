package com.example.manga_management.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class SmsService {
    private final String provider;
    private final String endpoint;
    private final String apiKey;
    private final String secretKey;
    private final String smsType;
    private final boolean sandbox;
    private final boolean templateReady;
    private final String otpTemplate;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public SmsService(@Value("${app.sms.provider:esms}") String provider,
                      @Value("${app.sms.esms.endpoint}") String endpoint,
                      @Value("${app.sms.esms.api-key:}") String apiKey,
                      @Value("${app.sms.esms.secret-key:}") String secretKey,
                      @Value("${app.sms.esms.sms-type:8}") String smsType,
                      @Value("${app.sms.esms.sandbox:true}") boolean sandbox,
                      @Value("${app.sms.esms.template-ready:false}") boolean templateReady,
                      @Value("${app.sms.otp-template}") String otpTemplate) {
        this.provider = valueOrEmpty(provider);
        this.endpoint = valueOrEmpty(endpoint);
        this.apiKey = valueOrEmpty(apiKey);
        this.secretKey = valueOrEmpty(secretKey);
        this.smsType = valueOrEmpty(smsType);
        this.sandbox = sandbox;
        this.templateReady = templateReady;
        this.otpTemplate = valueOrEmpty(otpTemplate);
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void sendOtp(String phone, String otp) {
        validateConfiguration();

        String normalizedPhone = normalizeVietnameseMobile(phone);
        String content = otpTemplate.replace("{OTP}", otp);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ApiKey", apiKey);
        payload.put("SecretKey", secretKey);
        payload.put("Phone", normalizedPhone);
        payload.put("Content", content);
        payload.put("SmsType", smsType);
        payload.put("IsUnicode", "0");
        payload.put("Sandbox", sandbox ? "1" : "0");
        payload.put("RequestId", UUID.randomUUID().toString());

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(20))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("eSMS trả về HTTP " + response.statusCode());
            }

            JsonNode result = objectMapper.readTree(response.body());
            String code = result.path("CodeResult").asText("");
            if (!"100".equals(code)) {
                String detail = result.path("ErrorMessage").asText("Không có mô tả lỗi");
                throw new IllegalStateException("eSMS từ chối yêu cầu (mã " + code + "): " + detail);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gửi SMS bị gián đoạn", exception);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể gửi OTP qua eSMS: " + exception.getMessage(), exception);
        }
    }

    private void validateConfiguration() {
        if (!"esms".equalsIgnoreCase(provider)) {
            throw new IllegalStateException("SMS_PROVIDER phải được đặt thành esms");
        }
        if (endpoint.isBlank() || apiKey.isBlank() || secretKey.isBlank()) {
            throw new IllegalStateException("API Key hoặc Secret Key của eSMS chưa được cấu hình");
        }
        if (!"8".equals(smsType)) {
            throw new IllegalStateException("Project đang hỗ trợ OTP đầu số cố định eSMS với SmsType=8");
        }
        if (!sandbox && !templateReady) {
            throw new IllegalStateException(
                    "Mẫu OTP eSMS chưa được xác nhận. Hãy chờ eSMS duyệt rồi đặt ESMS_TEMPLATE_READY=true");
        }
        if (!otpTemplate.contains("{OTP}")) {
            throw new IllegalStateException("SMS_OTP_TEMPLATE phải chứa biến {OTP}");
        }
    }

    private String normalizeVietnameseMobile(String phone) {
        String normalized = valueOrEmpty(phone).replaceAll("[^0-9+]", "");
        if (normalized.startsWith("+84")) {
            normalized = "0" + normalized.substring(3);
        } else if (normalized.startsWith("84")) {
            normalized = "0" + normalized.substring(2);
        }
        if (!normalized.matches("0(?:3|5|7|8|9)\\d{8}")) {
            throw new IllegalArgumentException("Số điện thoại di động Việt Nam không hợp lệ");
        }
        return normalized;
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
