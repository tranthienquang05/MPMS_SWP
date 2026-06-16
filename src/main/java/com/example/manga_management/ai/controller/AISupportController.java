package com.example.manga_management.ai.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.manga_management.ai.dto.AIRequestDTO;
import com.example.manga_management.ai.dto.AIResponseDTO;
import com.example.manga_management.ai.enums.AIFeature;
import com.example.manga_management.ai.service.AISupportService;

@RestController
@RequestMapping("/api/ai")
public class AISupportController {

    private final AISupportService aiSupportService;

    public AISupportController(AISupportService aiSupportService) {
        this.aiSupportService = aiSupportService;
    }

    /**
     * Lấy danh sách tất cả tính năng AI có sẵn.
     */
    @GetMapping("/features")
    public ResponseEntity<List<Map<String, String>>> getFeatures() {
        List<Map<String, String>> features = Arrays.stream(AIFeature.values())
                .map(f -> Map.of(
                        "code", f.getCode(),
                        "type", f.getType(),
                        "description", f.getBasePrompt()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(features);
    }

    /**
     * Chạy một tính năng AI hỗ trợ manga.
     */
    @PostMapping("/run")
    public ResponseEntity<AIResponseDTO> runFeature(@RequestBody AIRequestDTO request) {
        AIResponseDTO response = aiSupportService.runFeature(request);
        if ("error".equals(response.getStatus())) {
            return ResponseEntity.internalServerError().body(response);
        }
        return ResponseEntity.ok(response);
    }
}

