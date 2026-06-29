package com.example.manga_management.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.manga_management.entity.MangaPage;
import com.example.manga_management.entity.Submission;
import com.example.manga_management.repository.MangaPageRepository;
import com.example.manga_management.repository.SubmissionRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/submission")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionRepository submissionRepository;
    private final MangaPageRepository mangaPageRepository;

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

            submissionRepository.save(submission);

            // ✅ THÊM: cập nhật luôn page.filePath
            MangaPage mangaPage = mangaPageRepository.findById(submission.getPageId().getId())
                    .orElse(null);
            if (mangaPage != null) {
                String pageDir = "src/main/resources/static/MangaPage/";
                Files.createDirectories(Paths.get(pageDir));
                Path pageFilePath = Paths.get(pageDir + mangaPage.getId() + ".png");
                Files.write(pageFilePath, imageBytes);
                mangaPage.setFilePath("/MangaPage/" + mangaPage.getId() + ".png");
                mangaPageRepository.save(mangaPage);
            }

            return Map.of("status", "success", "redirectUrl", "/manga/assistant");

        } catch (IllegalArgumentException e) {

            return Map.of("status", "error", "message", "Base64 không hợp lệ");

        } catch (IOException e) {

            return Map.of("status", "error", "message", "Lỗi ghi file: " + e.getMessage());

        } catch (Exception e) {

            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    @PostMapping("/{submissionId}/edit")
    public Map<String, String> editSubmission(@PathVariable String submissionId,
            @RequestBody Map<String, String> body) {
        try {
            // 1. Tìm kiếm submission từ database
            Submission submission = submissionRepository.findById(submissionId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy submission"));
            MangaPage mangaPage = mangaPageRepository.findById(submission.getPageId().getId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy trang manga"));

            // 2. Lấy các tham số từ request body
            String base64 = body.get("imageBase64");
            String status = body.get("status");
            String comment = body.get("comment");

            // 3. Cập nhật các thông tin text
            if (status != null && !status.isBlank()) {
                submission.setStatus(status);
            }
            if (comment != null) {
                submission.setComment(comment);
            }

            // 4. Xử lý và lưu file hình ảnh
            byte[] imageBytes = null;
            if (base64 != null && !base64.isBlank()) {
                if (base64.contains(",")) {
                    base64 = base64.split(",")[1];
                }

                imageBytes = Base64.getDecoder().decode(base64);

                String uploadDir = "src/main/resources/static/Submission/";
                Files.createDirectories(Paths.get(uploadDir));

                String fileName = submissionId + ".png";
                Path filePath = Paths.get(uploadDir + fileName);
                Files.write(filePath, imageBytes);

                submission.setFilePath("/Submission/" + fileName);
            }

            // 5. Xử lý khi status là "pass"
            // Luôn cập nhật ảnh page khi có ảnh mới
            if (imageBytes != null) {
                String pageDir = "src/main/resources/static/MangaPage/";
                Files.createDirectories(Paths.get(pageDir));

                Path pageFilePath = Paths.get(pageDir + mangaPage.getId() + ".png");
                Files.write(pageFilePath, imageBytes);
                mangaPage.setFilePath("/MangaPage/" + mangaPage.getId() + ".png");
            }

            // Chỉ đổi status page khi status là "pass"
            if ("pass".equals(status)) {
                mangaPage.setStatus("pass");
            }

            mangaPageRepository.save(mangaPage);

            // 6. Lưu submission vào database
            submissionRepository.save(submission);

            String seriesId = submission.getPageId().getChapter().getSeries().getId();
            String chapterId = submission.getPageId().getChapter().getId();

            return Map.of("status", "success", "redirectUrl", "/manga/mangaka/myseries/" + seriesId + "/" + chapterId);

        } catch (IllegalArgumentException e) {
            return Map.of("status", "error", "message", "Base64 không hợp lệ");
        } catch (IOException e) {
            return Map.of("status", "error", "message", "Lỗi ghi file: " + e.getMessage());
        } catch (Exception e) {
            return Map.of("status", "error", "message", e.getMessage());
        }
    }
}
