package com.example.manga_management.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.manga_management.entity.Assistant;
import com.example.manga_management.entity.MangaPage;
import com.example.manga_management.entity.Submission;
import com.example.manga_management.entity.User;
import com.example.manga_management.repository.AssistantRepository;
import com.example.manga_management.repository.MangaPageRepository;
import com.example.manga_management.repository.SubmissionRepository;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/page")
public class PageController {
    @Autowired
    private final AssistantRepository assistantRepository;

    @Autowired
    private final SubmissionRepository submissionRepository;
    @Autowired
    private final MangaPageRepository mangaPageRepository;

    public PageController(AssistantRepository assistantRepository, SubmissionRepository submissionRepository,
            MangaPageRepository mangaPageRepository) {
        this.assistantRepository = assistantRepository;
        this.submissionRepository = submissionRepository;
        this.mangaPageRepository = mangaPageRepository;
    }

    @PostMapping("/{pageId}/savefile")
    @ResponseBody
    public Map<String, String> savePageFile(
        @PathVariable String pageId, 
        @RequestBody Map<String, String> body) {

        try {
            MangaPage page = mangaPageRepository.findById(pageId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy page: " + pageId));

            String base64 = body.get("imageBase64");
            if (base64 != null && base64.contains(",")) {
                base64 = base64.split(",")[1];
            }
            if (base64 == null || base64.isBlank()) {
                return Map.of("status", "error", "message", "imageBase64 trống");
            }

            byte[] imageBytes = Base64.getDecoder().decode(base64);

            String uploadDir = "src/main/resources/static/MangaPage/";
            Files.createDirectories(Paths.get(uploadDir));

            String fileName = pageId + ".png";
            Path filePath = Paths.get(uploadDir + fileName);
            Files.write(filePath, imageBytes);

            page.setFilePath("/MangaPage/" + fileName);
            page.setStatus("unfinish");
            mangaPageRepository.save(page);

            String chapterId = page.getChapter().getId();
            String seriesId = page.getChapter().getSeries().getId();

            return Map.of("status", "success", "redirectUrl", "/manga/mangaka/myseries/" + seriesId + "/" + chapterId);

        } catch (IllegalArgumentException e) {
            return Map.of("status", "error", "message", "Base64 không hợp lệ");
        } catch (IOException e) {
            return Map.of("status", "error", "message", "Lỗi ghi file: " + e.getMessage());
        } catch (Exception e) {
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    @GetMapping("/{pageId}/submission")
    @ResponseBody
    public Map<String, Object> getPageSubmission(@PathVariable String pageId) {
        Submission submission = submissionRepository.findByPageId(pageId);
        if (submission == null) {
            return Map.of("hasSubmission", false);
        }
        return Map.of("hasSubmission", true, "assistantName", submission.getAssistant().getUser().getFullname(),
                "status", submission.getStatus());
    }

    @PostMapping("/{pageId}/assign")
    @ResponseBody
    public Map<String, Object> assignPage(@PathVariable String pageId, @RequestBody Map<String, String> body,
            HttpSession session) {

        try {

            User user = (User) session.getAttribute("user");

            if (user == null) {
                return Map.of("status", "error", "message", "Chưa đăng nhập");
            }

            MangaPage page = mangaPageRepository.findById(pageId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy page"));

            if (submissionRepository.existsByPageIdIdAndStatus(pageId, "unfinish")) {
                return Map.of("status", "error", "message", "Trang này đã được giao");
            }

            String assistantId = body.get("assistantId");
            String comment = body.get("comment");
            String deadlineStr = body.get("deadline");

            Assistant assistant = assistantRepository.findById(assistantId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy assistant"));

            LocalDate deadline = null;

            if (deadlineStr != null && !deadlineStr.isBlank()) {

                deadline = LocalDate.parse(deadlineStr);

                if (!deadline.isAfter(LocalDate.now())) {
                    return Map.of("status", "error", "message", "Deadline phải sau ngày hiện tại");
                }
            }

            String lastId = submissionRepository.findTopByOrderByIdDesc().map(Submission::getId).orElse("SUB0000");

            int num = Integer.parseInt(lastId.replaceAll("[^0-9]", "")) + 1;

            String newId = "SUB" + String.format("%04d", num);

            Submission submission = new Submission();

            submission.setId(newId);
            submission.setPageId(page);
            submission.setAssistant(assistant);
            submission.setComment(comment);
            submission.setDeadline(deadline);
            submission.setStatus("unfinish");

            // Copy file page -> submission
            if (page.getFilePath() != null && !page.getFilePath().isBlank()) {

                String oldPath = page.getFilePath();

                String fileName = newId + ".png";

                Path source = Paths.get("src/main/resources/static" + oldPath);

                Path targetDir = Paths.get("src/main/resources/static/Submission");

                Files.createDirectories(targetDir);

                Path target = targetDir.resolve(fileName);

                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);

                submission.setFilePath("/Submission/" + fileName);
            }

            submissionRepository.save(submission);

            return Map.of("status", "success", "message", "Giao việc thành công");

        } catch (Exception e) {

            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    // API 3: Lấy danh sách assistant của mangaka (để hiện dropdown giao việc)
    
}