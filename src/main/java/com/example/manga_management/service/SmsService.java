package com.example.manga_management.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class SmsService {
    private final String endpoint;
    private final String bearerToken;
    private final HttpClient httpClient;

    public SmsService(@Value("${app.sms.endpoint:}") String endpoint,
                      @Value("${app.sms.bearer-token:}") String bearerToken) {
        this.endpoint = endpoint == null ? "" : endpoint.trim();
        this.bearerToken = bearerToken == null ? "" : bearerToken.trim();
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public void sendOtp(String phone, String otp) {
        if (endpoint.isBlank()) {
            throw new IllegalStateException("Dịch vụ SMS chưa được cấu hình trên máy chủ");
        }
        try {
            String message = "SANKYUU - Ma OTP cua ban la " + otp + ". Ma co hieu luc trong 5 phut.";
            String payload = "{\"to\":\"" + jsonEscape(phone) + "\",\"message\":\""
                    + jsonEscape(message) + "\"}";
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(15))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(payload));
            if (!bearerToken.isBlank()) {
                builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Cổng SMS trả về mã lỗi " + response.statusCode());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gửi SMS bị gián đoạn", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể gửi SMS: " + exception.getMessage(), exception);
        }
    }

    private String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }
}
