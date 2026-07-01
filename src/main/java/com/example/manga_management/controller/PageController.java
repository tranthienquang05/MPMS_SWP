package com.example.manga_management.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
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
    public Map<String, String> savePageFile(@PathVariable String pageId,
            @RequestBody Map<String, String> body) {
        Map<String, String> result = new HashMap<>();

        try {
            MangaPage page = mangaPageRepository.findById(pageId).orElse(null);
            if (page == null) {
                result.put("status", "error");
                result.put("message", "Không tìm thấy page: " + pageId);
                return result;
            }

            String base64 = body.get("imageBase64");
            if (base64 != null && base64.contains(",")) {
                base64 = base64.split(",")[1];
            }
            if (base64 == null || base64.isBlank()) {
                result.put("status", "error");
                result.put("message", "imageBase64 trống");
                return result;
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

            result.put("status", "success");
            result.put("message", "Lưu trang thành công!");
            result.put("redirectUrl", "/manga/mangaka/myseries/" + seriesId + "/" + chapterId);
        } catch (IllegalArgumentException e) {
            result.put("status", "error");
            result.put("message", "Base64 không hợp lệ");
        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", "Lỗi ghi file: " + e.getMessage());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }

    @GetMapping("/{pageId}/submission")
    @ResponseBody
    public Map<String, Object> getPageSubmission(@PathVariable String pageId) {
        Map<String, Object> result = new HashMap<>();

        Optional<Submission> optSubmission = submissionRepository.findByPageIdId(pageId);
        if (optSubmission.isEmpty()) {
            result.put("status", "success");
            result.put("hasSubmission", false);
            return result;
        }

        Submission submission = optSubmission.get();
        result.put("status", "success");
        result.put("hasSubmission", true);
        result.put("assistantName", submission.getAssistant().getUser().getFullname());
        result.put("submissionStatus", submission.getStatus());
        return result;
    }

    @PostMapping("/{pageId}/assign")
    @ResponseBody
    public Map<String, Object> assignPage(@PathVariable String pageId,
            @RequestBody Map<String, String> body,
            HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        try {
            User user = (User) session.getAttribute("user");
            if (user == null) {
                result.put("status", "error");
                result.put("message", "Chưa đăng nhập");
                return result;
            }

            MangaPage page = mangaPageRepository.findById(pageId).orElse(null);
            if (page == null) {
                result.put("status", "error");
                result.put("message", "Không tìm thấy page: " + pageId);
                return result;
            }

            if (page.getChapter() == null || !"unfinish".equals(page.getChapter().getStatus())) {
                result.put("status", "error");
                result.put("message", "Chỉ có thể giao việc khi chapter đang ở trạng thái unfinish");
                return result;
            }

            if ("finish".equals(page.getStatus())) {
                result.put("status", "error");
                result.put("message", "Trang này đã được đánh dấu hoàn thành");
                return result;
            }

            Optional<Submission> existingSubmissionOpt = submissionRepository.findByPageIdId(pageId);
            if (existingSubmissionOpt.isPresent() && "intask".equals(existingSubmissionOpt.get().getStatus())) {
                result.put("status", "error");
                result.put("message", "Trang này đang được giao");
                return result;
            }

            String assistantId = body.get("assistantId");
            String comment = body.get("comment");
            String deadlineStr = body.get("deadline");

            Assistant assistant = assistantRepository.findById(assistantId).orElse(null);
            if (assistant == null) {
                result.put("status", "error");
                result.put("message", "Không tìm thấy assistant: " + assistantId);
                return result;
            }

            if (deadlineStr == null || deadlineStr.isBlank()) {
                result.put("status", "error");
                result.put("message", "Deadline là bắt buộc");
                return result;
            }

            LocalDateTime deadline = LocalDateTime.parse(deadlineStr);
            if (!deadline.isAfter(LocalDateTime.now())) {
                result.put("status", "error");
                result.put("message", "Deadline phải sau ngày hiện tại");
                return result;
            }

            Submission submission = existingSubmissionOpt.orElseGet(() -> {
                String lastId = submissionRepository.findTopByOrderByIdDesc()
                        .map(Submission::getId).orElse("SUB0000");
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
                String fileName = submission.getId() + ".png";
                Path source = Paths.get("src/main/resources/static" + page.getFilePath());
                Path targetDir = Paths.get("src/main/resources/static/Submission");
                Files.createDirectories(targetDir);
                Files.copy(source, targetDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
                submission.setFilePath("/Submission/" + fileName);
            }

            submissionRepository.save(submission);
            page.setStatus("intask");
            mangaPageRepository.save(page);

            result.put("status", "success");
            result.put("message", "Giao việc thành công!");
            result.put("submissionId", submission.getId());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }

    @PostMapping("/{pageId}/finish")
    @ResponseBody
    public Map<String, Object> finishPage(@PathVariable String pageId) {
        Map<String, Object> result = new HashMap<>();

        MangaPage page = mangaPageRepository.findById(pageId).orElse(null);
        if (page == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy trang!");
            return result;
        }
        if ("finish".equals(page.getStatus())) {
            result.put("status", "error");
            result.put("message", "Trang đã hoàn thành rồi!");
            return result;
        }
        if ("intask".equals(page.getStatus())) {
            result.put("status", "error");
            result.put("message", "Không thể hoàn thành vì trợ lý đang làm việc này!");
            return result;
        }
        if ("done".equals(page.getStatus())) {
            Optional<Submission> subOpt = submissionRepository.findByPageIdId(pageId);
            if (subOpt.isPresent() && "done".equals(subOpt.get().getStatus())) {
                result.put("status", "error");
                result.put("message", "Bạn phải ấn nút Duyệt trước khi hoàn thành!");
                return result;
            }
        }

        page.setStatus("finish");
        mangaPageRepository.save(page);

        result.put("status", "success");
        result.put("message", "Đã hoàn thành trang!");
        result.put("pageId", pageId);
        return result;
    }

    @PostMapping("/{pageId}/approve-done")
    @ResponseBody
    public Map<String, Object> approvePageDone(@PathVariable String pageId) {
        Map<String, Object> result = new HashMap<>();

        MangaPage page = mangaPageRepository.findById(pageId).orElse(null);
        if (page == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy trang!");
            return result;
        }

        Optional<Submission> subOpt = submissionRepository.findByPageIdId(pageId);
        if (subOpt.isEmpty() || !"done".equals(subOpt.get().getStatus())) {
            result.put("status", "error");
            result.put("message", "Trợ lý chưa nộp bài!");
            return result;
        }

        Submission sub = subOpt.get();
        sub.setStatus("finish");
        sub.setApprovedAt(LocalDateTime.now());
        submissionRepository.save(sub);

        result.put("status", "success");
        result.put("message", "Đã duyệt! Bấm Hoàn thành để kết thúc trang.");
        result.put("pageId", pageId);
        return result;
    }

    @PostMapping("/{pageId}/reject-done")
    @ResponseBody
    public Map<String, Object> rejectPageDone(@PathVariable String pageId) {
        Map<String, Object> result = new HashMap<>();

        MangaPage page = mangaPageRepository.findById(pageId).orElse(null);
        if (page == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy trang!");
            return result;
        }

        Optional<Submission> subOpt = submissionRepository.findByPageIdId(pageId);
        if (subOpt.isEmpty() || !"done".equals(subOpt.get().getStatus())) {
            result.put("status", "error");
            result.put("message", "Trợ lý chưa nộp bài!");
            return result;
        }

        Submission sub = subOpt.get();
        sub.setStatus("intask");
        submissionRepository.save(sub);

        page.setStatus("intask");
        mangaPageRepository.save(page);

        result.put("status", "success");
        result.put("message", "Đã giao lại cho trợ lý!");
        result.put("pageId", pageId);
        return result;
    }

    @PostMapping("/{pageId}/reassign")
    @ResponseBody
    public Map<String, Object> reassignPage(@PathVariable String pageId,
            @RequestBody Map<String, String> body,
            HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        try {
            User user = (User) session.getAttribute("user");
            if (user == null) {
                result.put("status", "error");
                result.put("message", "Chưa đăng nhập");
                return result;
            }

            String submissionId = body.get("submissionId");
            String assistantId = body.get("assistantId");
            String comment = body.get("comment");
            String deadlineStr = body.get("deadline");

            Submission submission = submissionRepository.findById(submissionId).orElse(null);
            if (submission == null) {
                result.put("status", "error");
                result.put("message", "Không tìm thấy submission: " + submissionId);
                return result;
            }

            if (submission.getPageId() == null || !pageId.equals(submission.getPageId().getId())) {
                result.put("status", "error");
                result.put("message", "Submission không thuộc page này");
                return result;
            }

            MangaPage page = mangaPageRepository.findById(pageId).orElse(null);
            if (page == null) {
                result.put("status", "error");
                result.put("message", "Không tìm thấy page: " + pageId);
                return result;
            }

            if (page.getChapter() == null || !"unfinish".equals(page.getChapter().getStatus())) {
                result.put("status", "error");
                result.put("message", "Chỉ có thể giao lại khi chapter đang ở trạng thái unfinish");
                return result;
            }

            if ("finish".equals(page.getStatus())) {
                result.put("status", "error");
                result.put("message", "Trang này đã được đánh dấu hoàn thành");
                return result;
            }

            Assistant assistant = assistantRepository.findById(assistantId).orElse(null);
            if (assistant == null) {
                result.put("status", "error");
                result.put("message", "Không tìm thấy assistant: " + assistantId);
                return result;
            }

            if (deadlineStr == null || deadlineStr.isBlank()) {
                result.put("status", "error");
                result.put("message", "Deadline là bắt buộc");
                return result;
            }

            LocalDateTime deadline = LocalDateTime.parse(deadlineStr);
            if (!deadline.isAfter(LocalDateTime.now())) {
                result.put("status", "error");
                result.put("message", "Deadline phải sau ngày hiện tại");
                return result;
            }

            submission.setAssistant(assistant);
            submission.setComment(comment);
            submission.setDeadline(deadline);
            submission.setStatus("intask");
            submissionRepository.save(submission);

            page.setStatus("intask");
            mangaPageRepository.save(page);

            result.put("status", "success");
            result.put("message", "Đã giao lại thành công!");
            result.put("submissionId", submissionId);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }
}
