package com.example.manga_management.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.manga_management.entity.Assistant;
import com.example.manga_management.entity.FrameTask;
import com.example.manga_management.entity.MangaPage;
import com.example.manga_management.entity.Submission;
import com.example.manga_management.entity.User;
import com.example.manga_management.repository.AssistantRepository;
import com.example.manga_management.repository.FrameTaskRepository;
import com.example.manga_management.repository.MangaPageRepository;
import com.example.manga_management.repository.SubmissionRepository;
import com.example.manga_management.service.ActivityLogService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/page")
@Tag(name = "Page", description = "Giao việc vẽ trang cho trợ lý, duyệt bài, và nhận xét của tantou trên từng trang")
public class PageController {

    @Autowired
    private final AssistantRepository assistantRepository;
    @Autowired
    private final SubmissionRepository submissionRepository;
    @Autowired
    private final MangaPageRepository mangaPageRepository;
    @Autowired
    private final FrameTaskRepository frameTaskRepository;
    private final NotificationController notificationController;
    private final ActivityLogService activityLogService;

    public PageController(AssistantRepository assistantRepository, SubmissionRepository submissionRepository,
            MangaPageRepository mangaPageRepository, FrameTaskRepository frameTaskRepository,
            NotificationController notificationController, ActivityLogService activityLogService) {
        this.assistantRepository = assistantRepository;
        this.submissionRepository = submissionRepository;
        this.mangaPageRepository = mangaPageRepository;
        this.frameTaskRepository = frameTaskRepository;
        this.notificationController = notificationController;
        this.activityLogService = activityLogService;
    }

    /** Mô tả ngắn gọn 1 trang, dùng chung cho nội dung thông báo/log. */
    private String describePage(MangaPage page) {
        if (page.getChapter() == null) {
            return "trang " + page.getId();
        }
        String seriesName = page.getChapter().getSeries() != null
                ? page.getChapter().getSeries().getSeriesName() : "—";
        return "trang " + page.getPageNumber() + " (Chapter " + page.getChapter().getChapterNumber()
                + " - " + seriesName + ")";
    }

    /**
     * Chỉ về "untask" khi tất cả task của assistant đã được duyệt hết (không còn
     * submission intask hoặc done chờ duyệt).
     */
    private void refreshAssistantStatus(Assistant assistant) {
        boolean hasUnapprovedTask = !submissionRepository
                .findByAssistant_IdAndStatus(assistant.getId(), "intask").isEmpty()
                || !submissionRepository.findByAssistant_IdAndStatus(assistant.getId(), "done").isEmpty();
        assistant.setStatus(hasUnapprovedTask ? "intask" : "untask");
        assistantRepository.save(assistant);
    }

    @Operation(summary = "Lưu ảnh bản vẽ (base64) cho một trang")
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
            // Không ép status về "unfinish" ở đây — nếu trang đã có task
            // (intask/done/finish), lưu bản vẽ (vd sửa lại sau khi duyệt) không
            // được phép làm mất trạng thái đó. Trang mới tạo vốn đã "unfinish"
            // sẵn nên không cần set lại.
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

    @Operation(summary = "Thông tin submission (bài nộp) gần nhất của một trang")
    @GetMapping("/{pageId}/submission")
    @ResponseBody
    public Map<String, Object> getPageSubmission(@PathVariable String pageId) {
        Map<String, Object> result = new HashMap<>();

        Optional<Submission> optSubmission = submissionRepository.findTopByPageIdIdOrderByCreatedAtDesc(pageId);
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

    @Operation(summary = "Mangaka giao vẽ 1 trang cho trợ lý (kèm deadline + note từng frame)")
    @PostMapping("/{pageId}/assign")
    @ResponseBody
    @Transactional
    public Map<String, Object> assignPage(@PathVariable String pageId,
            @RequestBody Map<String, Object> body,
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

            if (page.getChapter().getSeries() != null
                    && page.getChapter().getSeries().isLocked()) {
                result.put("status", "error");
                result.put("message", page.getChapter().getSeries().getLockMessage());
                return result;
            }

            if ("finish".equals(page.getStatus())) {
                result.put("status", "error");
                result.put("message", "Trang này đã được đánh dấu hoàn thành");
                return result;
            }

            // Trang có thể trải qua nhiều vòng giao việc (nhiều assistant khác nhau
            // theo thời gian) — chỉ chặn khi task gần nhất còn dang dở (đang làm
            // hoặc đã nộp nhưng chưa duyệt/giao lại). Sau khi đã duyệt xong thì mở
            // ra cho vòng giao việc mới.
            Optional<Submission> latestSubmissionOpt = submissionRepository.findTopByPageIdIdOrderByCreatedAtDesc(pageId);
            if (latestSubmissionOpt.isPresent()) {
                String latestStatus = latestSubmissionOpt.get().getStatus();
                if ("intask".equals(latestStatus)) {
                    result.put("status", "error");
                    result.put("message", "Trang này đang được giao");
                    return result;
                }
                if ("done".equals(latestStatus)) {
                    result.put("status", "error");
                    result.put("message", "Trang này đang chờ duyệt, hãy Duyệt hoặc Giao lại trước khi giao task mới");
                    return result;
                }
            }

            String assistantId = (String) body.get("assistantId");
            String comment = (String) body.get("comment");
            String deadlineStr = (String) body.get("deadline");
            List<?> frameNotes = body.get("frameNotes") instanceof List<?> notes ? notes : null;

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

            if (frameNotes == null || frameNotes.isEmpty()) {
                result.put("status", "error");
                result.put("message", "Vui lòng chọn số frame và nhập note cho từng frame!");
                return result;
            }
            if (frameNotes.size() > 20) {
                result.put("status", "error");
                result.put("message", "Số frame tối đa là 20 cho mỗi task!");
                return result;
            }
            for (Object note : frameNotes) {
                if (note != null && note.toString().length() > 1000) {
                    result.put("status", "error");
                    result.put("message", "Note mỗi frame tối đa 1000 chữ!");
                    return result;
                }
            }

            LocalDateTime deadline = LocalDateTime.parse(deadlineStr);
            if (!deadline.isAfter(LocalDateTime.now())) {
                result.put("status", "error");
                result.put("message", "Deadline phải sau ngày hiện tại");
                return result;
            }

            // Luôn tạo submission mới cho mỗi lần giao việc — không ghi đè submission
            // cũ đã duyệt/hoàn thành, để giữ lại lịch sử từng vòng giao việc (vd
            // ass1 vẽ bối cảnh xong duyệt, rồi giao vòng mới cho ass2 viết thoại).
            String lastId = submissionRepository.findTopByOrderByIdDesc()
                    .map(Submission::getId).orElse("SUB0000");
            int num = Integer.parseInt(lastId.replaceAll("[^0-9]", "")) + 1;
            String newId = "SUB" + String.format("%04d", num);
            Submission submission = new Submission();
            submission.setId(newId);

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

            // Lưu note công việc cho từng frame của vòng giao việc mới này
            Optional<FrameTask> lastFrameTask = frameTaskRepository.findTopByOrderByIdDesc();
            int maxFrameTaskId = 0;
            if (lastFrameTask.isPresent()) {
                maxFrameTaskId = Integer.parseInt(lastFrameTask.get().getId().substring(2));
            }
            int frameNumber = 1;
            for (Object note : frameNotes) {
                FrameTask frameTask = new FrameTask();
                frameTask.setId("FT" + String.format("%05d", ++maxFrameTaskId));
                frameTask.setPage(page);
                frameTask.setSubmission(submission);
                frameTask.setFrameNumber(frameNumber++);
                frameTask.setContent(note == null ? "" : note.toString());
                frameTaskRepository.save(frameTask);
            }

            // Giao việc xong thì assistant chuyển sang intask
            assistant.setStatus("intask");
            assistantRepository.save(assistant);

            if (assistant.getUser() != null) {
                notificationController.send(null, assistant.getUser().getId(),
                        "Bạn được giao " + describePage(page) + ", deadline "
                                + deadline.toLocalDate() + " " + deadline.toLocalTime() + ".",
                        "/manga/assistant");
            }

            result.put("status", "success");
            result.put("message", "Giao việc thành công!");
            result.put("submissionId", submission.getId());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }

    @Operation(summary = "Đánh dấu 1 trang đã hoàn thành xong (kết thúc vòng làm việc)")
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
        if (page.getChapter() != null && page.getChapter().getSeries() != null
                && page.getChapter().getSeries().isLocked()) {
            result.put("status", "error");
            result.put("message", page.getChapter().getSeries().getLockMessage());
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
            Optional<Submission> subOpt = submissionRepository.findTopByPageIdIdOrderByCreatedAtDesc(pageId);
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

    @Operation(summary = "Mangaka duyệt bài trợ lý vừa nộp cho 1 trang")
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
        if (page.getChapter() != null && page.getChapter().getSeries() != null
                && page.getChapter().getSeries().isLocked()) {
            result.put("status", "error");
            result.put("message", page.getChapter().getSeries().getLockMessage());
            return result;
        }

        Optional<Submission> subOpt = submissionRepository.findTopByPageIdIdOrderByCreatedAtDesc(pageId);
        if (subOpt.isEmpty() || !"done".equals(subOpt.get().getStatus())) {
            result.put("status", "error");
            result.put("message", "Trợ lý chưa nộp bài!");
            return result;
        }

        Submission sub = subOpt.get();
        sub.setStatus("finish");
        sub.setApprovedAt(LocalDateTime.now());
        submissionRepository.save(sub);

        // Nếu assistant không còn task nào chưa duyệt thì chuyển về untask
        if (sub.getAssistant() != null) {
            refreshAssistantStatus(sub.getAssistant());
            if (sub.getAssistant().getUser() != null) {
                notificationController.send(null, sub.getAssistant().getUser().getId(),
                        "Bài làm " + describePage(page) + " của bạn đã được duyệt!",
                        "/manga/assistant");
            }
        }

        result.put("status", "success");
        result.put("message", "Đã duyệt! Bấm Hoàn thành để kết thúc trang.");
        result.put("pageId", pageId);
        return result;
    }

    // @PostMapping("/{pageId}/reject-done")
    // @ResponseBody
    // public Map<String, Object> rejectPageDone(@PathVariable String pageId) {
    //     Map<String, Object> result = new HashMap<>();
    //     MangaPage page = mangaPageRepository.findById(pageId).orElse(null);
    //     if (page == null) {
    //         result.put("status", "error");
    //         result.put("message", "Không tìm thấy trang!");
    //         return result;
    //     }
    //     Optional<Submission> subOpt = submissionRepository.findByPageIdId(pageId);
    //     if (subOpt.isEmpty() || !"done".equals(subOpt.get().getStatus())) {
    //         result.put("status", "error");
    //         result.put("message", "Trợ lý chưa nộp bài!");
    //         return result;
    //     }
    //     Submission sub = subOpt.get();
    //     sub.setStatus("intask");
    //     submissionRepository.save(sub);
    //     page.setStatus("intask");
    //     mangaPageRepository.save(page);
    //     result.put("status", "success");
    //     result.put("message", "Đã giao lại cho trợ lý!");
    //     result.put("pageId", pageId);
    //     return result;
    // }
    @Operation(summary = "Giao lại trang cho đúng trợ lý cũ (đổi deadline/note, không đổi người)")
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

            if (page.getChapter().getSeries() != null
                    && page.getChapter().getSeries().isLocked()) {
                result.put("status", "error");
                result.put("message", page.getChapter().getSeries().getLockMessage());
                return result;
            }

            if ("finish".equals(page.getStatus())) {
                result.put("status", "error");
                result.put("message", "Trang này đã được đánh dấu hoàn thành");
                return result;
            }

            // "Giao lại" luôn giữ nguyên đúng trợ lý đang làm task này — không nhận
            // assistantId từ client để tránh bị đổi sang người khác (muốn giao cho
            // người khác thì dùng "Giao việc" để tạo task mới).
            Assistant assistant = submission.getAssistant();
            if (assistant == null) {
                result.put("status", "error");
                result.put("message", "Task này chưa có trợ lý để giao lại");
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

            submission.setComment(comment);
            submission.setDeadline(deadline);
            submission.setStatus("intask");
            submissionRepository.save(submission);

            page.setStatus("intask");
            mangaPageRepository.save(page);

            assistant.setStatus("intask");
            assistantRepository.save(assistant);

            if (assistant.getUser() != null) {
                notificationController.send(null, assistant.getUser().getId(),
                        "Bạn được giao lại " + describePage(page) + ", deadline "
                                + deadline.toLocalDate() + " " + deadline.toLocalTime() + ".",
                        "/manga/assistant");
            }
            activityLogService.log(user.getId(), "reassign-task",
                    "Đã giao lại " + describePage(page) + " cho " + assistant.getUser().getFullname());

            result.put("status", "success");
            result.put("message", "Đã giao lại thành công!");
            result.put("submissionId", submissionId);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }

    /** Tantou nhận xét riêng cho 1 trang (khác kịch bản) — mangaka xem được, có thông báo + lưu lịch sử. */
    @Operation(summary = "Tantou lưu nhận xét cho 1 trang (chặn nếu trang đã hoàn thành)")
    @PostMapping("/{pageId}/tantou-comment")
    @ResponseBody
    public Map<String, Object> saveTantouComment(@PathVariable String pageId,
            @RequestBody Map<String, String> body, HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập");
            return result;
        }

        MangaPage page = mangaPageRepository.findById(pageId).orElse(null);
        if (page == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy trang: " + pageId);
            return result;
        }

        if ("finish".equals(page.getStatus())) {
            result.put("status", "error");
            result.put("message", "Trang đã hoàn thành, không thể thêm nhận xét!");
            return result;
        }

        String comment = body.get("comment");
        if (comment == null || comment.trim().isEmpty()) {
            result.put("status", "error");
            result.put("message", "Vui lòng nhập nội dung nhận xét!");
            return result;
        }
        if (comment.trim().length() > 500) {
            result.put("status", "error");
            result.put("message", "Nhận xét tối đa 500 chữ!");
            return result;
        }

        page.setTantouComment(comment.trim());
        mangaPageRepository.save(page);

        if (page.getChapter() != null && page.getChapter().getSeries() != null
                && page.getChapter().getSeries().getProposal() != null
                && page.getChapter().getSeries().getProposal().getMangaka() != null
                && page.getChapter().getSeries().getProposal().getMangaka().getUser() != null) {
            String seriesId = page.getChapter().getSeries().getId();
            String chapterId = page.getChapter().getId();
            notificationController.send(null,
                    page.getChapter().getSeries().getProposal().getMangaka().getUser().getId(),
                    "Tantou vừa nhận xét " + describePage(page) + ": " + comment.trim(),
                    "/manga/mangaka/myseries/" + seriesId + "/" + chapterId);
        }

        activityLogService.log(user.getId(), "comment-page",
                "Đã nhận xét " + describePage(page) + ": " + comment.trim());

        result.put("status", "success");
        result.put("message", "Đã lưu nhận xét!");
        return result;
    }
}
