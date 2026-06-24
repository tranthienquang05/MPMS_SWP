package com.example.manga_management.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.manga_management.entity.Submission;
import com.example.manga_management.repository.SubmissionRepository;

import org.springframework.web.bind.annotation.RequestBody;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/submission")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionRepository submissionRepository;

    @PostMapping("/{submissionId}/savefile")
    public Map<String, String> saveSubmissionFile(@PathVariable String submissionId,
            @RequestBody Map<String, String> body) {

        try {

            Submission submission = submissionRepository.findById(submissionId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy submission"));

            String base64 = body.get("imageBase64");

            if (base64 == null || base64.isBlank()) {
                return Map.of("status", "error", "message", "Không có dữ liệu ảnh");
            }

            if (base64.contains(",")) {
                base64 = base64.split(",")[1];
            }

            byte[] imageBytes = Base64.getDecoder().decode(base64);

            String uploadDir = "src/main/resources/static/Submission/";

            Files.createDirectories(Paths.get(uploadDir));

            String fileName = submissionId + ".png";

            Path filePath = Paths.get(uploadDir + fileName);

            Files.write(filePath, imageBytes);

            submission.setFilePath("/Submission/" + fileName);

            submission.setStatus("unfinish");

            submissionRepository.save(submission);

            return Map.of("status", "success", "redirectUrl", "/manga/assistant/submission/view");

        } catch (IllegalArgumentException e) {

            return Map.of("status", "error", "message", "Base64 không hợp lệ");

        } catch (IOException e) {

            return Map.of("status", "error", "message", "Lỗi ghi file: " + e.getMessage());

        } catch (Exception e) {

            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    
}