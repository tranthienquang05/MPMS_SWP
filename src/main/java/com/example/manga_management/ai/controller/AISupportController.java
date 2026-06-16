package com.example.manga_management.ai.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.manga_management.ai.dto.AIRequestDTO;
import com.example.manga_management.ai.dto.AIResponseDTO;
import com.example.manga_management.ai.service.AISupportService;

@RestController
@RequestMapping("/api/ai")
public class AISupportController {

    private final AISupportService aiSupportService;

    public AISupportController(AISupportService aiSupportService) {
        this.aiSupportService = aiSupportService;
    }

    /**
     * Run an AI-assisted manga feature.
     *
     * @param request contains the feature code, optional user prompt, and optional base64 image
     * @return the AI processing result wrapped in a standard response DTO
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
