package com.example.manga_management.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
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
        Optional<Submission> optSubmission = submissionRepository.findByPageIdId(pageId);
        if (optSubmission.isEmpty()) {
            return Map.of("hasSubmission", false);
        }
        Submission submission = optSubmission.get();
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
                return Map.of("status", "error", "message", "Chua dang nhap");
            }

            MangaPage page = mangaPageRepository.findById(pageId)
                    .orElseThrow(() -> new RuntimeException("Khong tim thay page"));

            if (page.getChapter() == null || !"unfinish".equals(page.getChapter().getStatus())) {
                return Map.of("status", "error", "message", "Chi co the giao viec khi chapter dang o trang thai unfinish");
            }

            if ("finish".equals(page.getStatus())) {
                return Map.of("status", "error", "message", "Trang nay da duoc danh dau hoan thanh");
            }

            Optional<Submission> existingSubmissionOpt = submissionRepository.findByPageIdId(pageId);
            if (existingSubmissionOpt.isPresent() && "intask".equals(existingSubmissionOpt.get().getStatus())) {
                return Map.of("status", "error", "message", "Trang nay dang duoc giao");
            }

            String assistantId = body.get("assistantId");
            String comment = body.get("comment");
            String deadlineStr = body.get("deadline");

            Assistant assistant = assistantRepository.findById(assistantId)
                    .orElseThrow(() -> new RuntimeException("Khong tim thay assistant"));

            LocalDateTime deadline = null;

            if (deadlineStr != null && !deadlineStr.isBlank()) {

                deadline = LocalDateTime.parse(deadlineStr);

                if (!deadline.isAfter(LocalDateTime.now())) {
                    return Map.of("status", "error", "message", "Deadline phai sau ngay hien tai");
                }
            } else {
                return Map.of("status", "error", "message", "Deadline la bat buoc");
            }

            Submission submission = existingSubmissionOpt.orElseGet(() -> {
                String lastId = submissionRepository.findTopByOrderByIdDesc().map(Submission::getId)
                        .orElse("SUB0000");
                int num = Integer.parseInt(lastId.replaceAll("[^0-9]", "")) + 1;
                String newId = "SUB" + String.format("%04d", num);

                Submission newSubmission = new Submission();
                newSubmission.setId(newId);
                return newSubmission;
            });

            submission.setPageId(page);
            submission.setAssistant(assistant);
            submission.setComment(comment);
            submission.setDeadline(deadline);
            submission.setStatus("intask");

            if (page.getFilePath() != null && !page.getFilePath().isBlank()) {

                String oldPath = page.getFilePath();

                String fileName = submission.getId() + ".png";

                Path source = Paths.get("src/main/resources/static" + oldPath);

                Path targetDir = Paths.get("src/main/resources/static/Submission");

                Files.createDirectories(targetDir);

                Path target = targetDir.resolve(fileName);

                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);

                submission.setFilePath("/Submission/" + fileName);
            }

            submissionRepository.save(submission);

            page.setStatus("intask");
            mangaPageRepository.save(page);

            return Map.of("status", "success", "message", "Giao viec thanh cong");

        } catch (Exception e) {

            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    // API 3: Lay danh sach assistant cua mangaka (de hien dropdown giao viec)
    // API 3: Lấy danh sách assistant của mangaka (để hiện dropdown giao việc)
    // dòng comment trên không liên quan dưới này
    // Hoàn thành page: unfinish → finish
    @PostMapping("/{pageId}/finish")
    @ResponseBody
    public Map<String, Object> finishPage(@PathVariable String pageId) {
        MangaPage page = mangaPageRepository.findById(pageId).orElse(null);
        if (page == null) {
            return Map.of("status", "error", "message", "Không tìm thấy trang!");
        }
        if ("finish".equals(page.getStatus())) {
            return Map.of("status", "error", "message", "Trang đã hoàn thành rồi!");
        }
        if ("intask".equals(page.getStatus())) {
            return Map.of("status", "error", "message", "Không thể hoàn thành vì trợ lý đang làm việc này!");
        }
        if ("done".equals(page.getStatus())) {
            // Kiểm tra submission đã được duyệt chưa
            Optional<Submission> subOpt = submissionRepository.findByPageIdId(pageId);
            if (subOpt.isPresent() && "done".equals(subOpt.get().getStatus())) {
                return Map.of("status", "error", "message", "Bạn phải ấn nút Duyệt trước khi hoàn thành!");
            }
            // Submission đã finish → cho phép hoàn thành
        }
        page.setStatus("finish");
        mangaPageRepository.save(page);
        return Map.of("status", "success", "message", "Đã hoàn thành trang!");
    }

// Mangaka duyệt page done → finish
    @PostMapping("/{pageId}/approve-done")
    @ResponseBody
    public Map<String, Object> approvePageDone(@PathVariable String pageId) {
        MangaPage page = mangaPageRepository.findById(pageId).orElse(null);
        if (page == null) {
            return Map.of("status", "error", "message", "Không tìm thấy trang!");
        }

        Optional<Submission> subOpt = submissionRepository.findByPageIdId(pageId);
        if (subOpt.isEmpty() || !"done".equals(subOpt.get().getStatus())) {
            return Map.of("status", "error", "message", "Trợ lý chưa nộp bài!");
        }

        Submission sub = subOpt.get();
        sub.setStatus("finish");
        submissionRepository.save(sub);

        return Map.of("status", "success", "message", "Đã duyệt! Bấm Hoàn thành để kết thúc trang.");
    }

// Mangaka giao lại page done → intask
    // Mangaka giao lai page done -> intask
    @PostMapping("/{pageId}/reject-done")
    @ResponseBody
    public Map<String, Object> rejectPageDone(@PathVariable String pageId) {
        MangaPage page = mangaPageRepository.findById(pageId).orElse(null);
        if (page == null) {
            return Map.of("status", "error", "message", "Khong tim thay trang!");
        }

        Optional<Submission> subOpt = submissionRepository.findByPageIdId(pageId);
        if (subOpt.isEmpty() || !"done".equals(subOpt.get().getStatus())) {
            return Map.of("status", "error", "message", "Tro ly chua nop bai!");
        }

        // Giao lai: doi ca submission va page ve intask
        Submission sub = subOpt.get();
        sub.setStatus("intask");
        submissionRepository.save(sub);

        page.setStatus("intask");
        mangaPageRepository.save(page);
        return Map.of("status", "success", "message", "Da giao lai cho tro ly!");
    }

    @PostMapping("/{pageId}/reassign")
    @ResponseBody
    public Map<String, Object> reassignPage(@PathVariable String pageId,
            @RequestBody Map<String, String> body,
            HttpSession session) {
        try {
            User user = (User) session.getAttribute("user");
            if (user == null) {
                return Map.of("status", "error", "message", "Chua dang nhap");
            }

            String submissionId = body.get("submissionId");
            String assistantId = body.get("assistantId");
            String comment = body.get("comment");
            String deadlineStr = body.get("deadline");

            Submission submission = submissionRepository.findById(submissionId)
                    .orElseThrow(() -> new RuntimeException("Khong tim thay submission"));

            if (submission.getPageId() == null || !pageId.equals(submission.getPageId().getId())) {
                return Map.of("status", "error", "message", "Submission khong thuoc page nay");
            }

            MangaPage page = mangaPageRepository.findById(pageId)
                    .orElseThrow(() -> new RuntimeException("Khong tim thay page"));
            if (page.getChapter() == null || !"unfinish".equals(page.getChapter().getStatus())) {
                return Map.of("status", "error", "message", "Chi co the giao lai khi chapter dang o trang thai unfinish");
            }
            if ("finish".equals(page.getStatus())) {
                return Map.of("status", "error", "message", "Trang nay da duoc danh dau hoan thanh");
            }

            Assistant assistant = assistantRepository.findById(assistantId)
                    .orElseThrow(() -> new RuntimeException("Khong tim thay assistant"));

            LocalDateTime deadline = null;
            if (deadlineStr != null && !deadlineStr.isBlank()) {
                deadline = LocalDateTime.parse(deadlineStr);
                if (!deadline.isAfter(LocalDateTime.now())) {
                    return Map.of("status", "error", "message", "Deadline phai sau ngay hien tai");
                }
            } else {
                return Map.of("status", "error", "message", "Deadline la bat buoc");
            }

            submission.setAssistant(assistant);
            submission.setComment(comment);
            submission.setDeadline(deadline);
            submission.setStatus("intask");
            submissionRepository.save(submission);

            page.setStatus("intask");
            mangaPageRepository.save(page);

            return Map.of("status", "success", "message", "Da giao lai thanh cong!");
        } catch (Exception e) {
            return Map.of("status", "error", "message", e.getMessage());
        }
    }
}
