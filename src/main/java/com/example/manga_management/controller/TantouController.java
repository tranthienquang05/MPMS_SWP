package com.example.manga_management.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.example.manga_management.entity.Series;
import com.example.manga_management.entity.Chapter;
import com.example.manga_management.entity.Mangaka;
import com.example.manga_management.entity.Proposal;
import com.example.manga_management.entity.TantoEditor;
import com.example.manga_management.entity.User;
import com.example.manga_management.repository.ProposalRepository;
import com.example.manga_management.repository.TantoEditorRepository;
import com.example.manga_management.repository.MangakaRepository;
import com.example.manga_management.repository.SeriesRepository;
import com.example.manga_management.repository.ChapterRepository;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/manga/tantou")
public class TantouController {

    private final ProposalRepository proposalRepository;
    private final TantoEditorRepository tantoEditorRepository;
    private final NotificationController notificationController;
    private final MangakaRepository mangakaRepository;
    private final SeriesRepository seriesRepository;
    private final ChapterRepository chapterRepository;

    public TantouController(ProposalRepository proposalRepository, TantoEditorRepository tantoEditorRepository,
            NotificationController notificationController, MangakaRepository mangakaRepository,
            SeriesRepository seriesRepository, ChapterRepository chapterRepository) {
        this.proposalRepository = proposalRepository;
        this.tantoEditorRepository = tantoEditorRepository;
        this.notificationController = notificationController;
        this.mangakaRepository = mangakaRepository;
        this.seriesRepository = seriesRepository;
        this.chapterRepository = chapterRepository;
    }

    @GetMapping("")
    public String tantouPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null)
            return "redirect:/login";

        TantoEditor editor = tantoEditorRepository.findByUser(user).orElse(null);
        if (editor == null)
            return "redirect:/login";

        model.addAttribute("currentUserId", user.getId());
        model.addAttribute("tantouId", editor.getId());
        return "tantou";
    }

    @Operation(summary = "Danh sách bản thảo mới chờ duyệt")
    @GetMapping("/proposals")
    @ResponseBody
    public Map<String, Object> getProposals(@RequestParam String tantouId) {
        Map<String, Object> result = new HashMap<>();

        TantoEditor editor = tantoEditorRepository.findById(tantouId).orElse(null);
        if (editor == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy Tantou!");
            return result;
        }

        List<Proposal> list = proposalRepository.findByStatusAndMangaka_Editor_Id("new", editor.getId());

        result.put("status", "success");
        result.put("total", list.size());
        result.put("proposals", list);
        return result;
    }

    @Operation(summary = "Danh sách đề xuất đã được Tantou duyệt, chờ nộp lên hội đồng")
    @GetMapping("/approved-proposals")
    @ResponseBody
    public Map<String, Object> getApprovedProposals() {
        Map<String, Object> result = new HashMap<>();
        List<Proposal> list = proposalRepository.findByStatus("approved");
        result.put("status", "success");
        result.put("total", list.size());
        result.put("proposals", list);
        return result;
    }

    @Operation(summary = "Duyệt bản thảo: được duyệt / cần sửa / từ chối")
    @PostMapping("/review")
    @ResponseBody
    public Map<String, String> tantouReview(
            @RequestParam String id,
            @RequestParam String action, // "approve" | "revision" | "reject"
            @RequestParam(required = false) String comment,
            @RequestParam(required = false) Double score,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime deadline) {

        Map<String, String> result = new HashMap<>();
        Proposal p = proposalRepository.findById(id).orElse(null);
        if (p == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy đề xuất: " + id);
            return result;
        }
        if (!"new".equals(p.getStatus())) {
            result.put("status", "error");
            result.put("message", "Đề xuất này không ở trạng thái chờ duyệt!");
            return result;
        }

        p.setComment(comment);
        p.setEditorScore(score);

        switch (action) {
            case "approve" -> {
                p.setStatus("approved");
                String scoreText = score != null ? String.format(" Điểm chấm: %.2f/10.", score) : "";
                String commentText = (comment != null && !comment.isBlank()) ? " Nhận xét: " + comment.trim() : "";
                notificationController.send(null, p.getMangaka().getUser().getId(),
                        "Đề xuất \"" + p.getSeriesName() + "\" đã được biên tập viên duyệt và sẽ sớm gửi lên hội đồng."
                                + scoreText + commentText,
                        "/manga/mangaka/my-projects");
            }
            case "revision" -> {
                if (deadline == null) {
                    result.put("status", "error");
                    result.put("message", "Vui lòng chọn deadline sửa bài!");
                    return result;
                }
                p.setStatus("revision");
                p.setRevisionDeadline(deadline);

                String deadlineText = deadline.toLocalDate() + " lúc " + deadline.toLocalTime();
                String commentText = (comment != null && !comment.isBlank()) ? " Góp ý: " + comment.trim() : "";
                notificationController.send(null, p.getMangaka().getUser().getId(),
                        "Đề xuất \"" + p.getSeriesName() + "\" cần chỉnh sửa trước " + deadlineText + "." + commentText,
                        "/manga/mangaka/my-projects");
            }
            case "reject" -> {
                p.setStatus("locked");
                String commentText = (comment != null && !comment.isBlank()) ? " Lý do: " + comment.trim() : "";
                notificationController.send(null, p.getMangaka().getUser().getId(),
                        "Rất tiếc, đề xuất \"" + p.getSeriesName() + "\" đã bị từ chối." + commentText,
                        "/manga/mangaka/my-projects");
            }
            default -> {
                result.put("status", "error");
                result.put("message", "Hành động không hợp lệ!");
                return result;
            }
        }

        proposalRepository.save(p);
        result.put("status", "success");
        result.put("proposalId", id);
        result.put("newStatus", p.getStatus());

        // ✅ THÊM: set message phù hợp theo action để trả về cho frontend
        String successMessage = switch (action) {
            case "approve" -> "Đã duyệt đề xuất \"" + p.getSeriesName() + "\"!";
            case "revision" -> "Đã yêu cầu sửa lại đề xuất \"" + p.getSeriesName() + "\"!";
            case "reject" -> "Đã từ chối đề xuất \"" + p.getSeriesName() + "\"!";
            default -> "Cập nhật thành công!";
        };
        result.put("message", successMessage);

        return result;
    }

    @Operation(summary = "Nộp đề xuất đã duyệt lên hội đồng (kèm hồ sơ đề xuất)")
    @PostMapping(value = "/submit-to-board", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public Map<String, String> submitToBoard(
            @RequestParam String proposalId,
            @RequestPart MultipartFile fileOfTantou) {

        Map<String, String> result = new HashMap<>();
        Proposal p = proposalRepository.findById(proposalId).orElse(null);
        if (p == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy đề xuất: " + proposalId);
            return result;
        }
        if (!"approved".equals(p.getStatus())) {
            result.put("status", "error");
            result.put("message", "Chỉ đề xuất đã được duyệt mới có thể nộp lên hội đồng!");
            return result;
        }
        if (fileOfTantou.isEmpty()) {
            result.put("status", "error");
            result.put("message", "Vui lòng đính kèm hồ sơ đề xuất!");
            return result;
        }

        String fileNameOfTantou = fileOfTantou.getOriginalFilename();

        if (fileNameOfTantou == null ||
                !fileNameOfTantou.toLowerCase().endsWith(".pdf")) {

            result.put("status", "error");
            result.put("message", "Chỉ được phép tải lên file PDF!");
            return result;
        }

        try {
            String workingDir = System.getProperty("user.dir");
            String uploadDir = workingDir + File.separator + "src" + File.separator + "main" + File.separator
                    + "resources" + File.separator + "static" + File.separator + "tantou-profile" + File.separator;

            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String originalName = fileOfTantou.getOriginalFilename();
            String extension = ".pdf";
            if (originalName != null && originalName.contains(".")) {
                extension = originalName.substring(originalName.lastIndexOf("."));
            }

            String fileName = proposalId + extension;
            fileOfTantou.transferTo(uploadPath.resolve(fileName).toFile());

            p.setFileOfTantou("/tantou-profile/" + fileName);
            p.setStatus("board_check");
            proposalRepository.save(p);

            notificationController.send("board", null,
                    "Có dự án mới '" + p.getSeriesName() + "' cần bỏ phiếu!", "/manga/editor");

            result.put("status", "success");
            result.put("proposalId", proposalId);
            result.put("newStatus", p.getStatus());
            result.put("message", "Đã nộp lên hội đồng!");
        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }

    // ================== MỚI THÊM CHO TÍNH NĂNG 2 ==================
    @GetMapping("/my-mangakas")
    @ResponseBody
    public Map<String, Object> getMyMangakas(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập!");
            return result;
        }

        TantoEditor editor = tantoEditorRepository.findByUser(user).orElse(null);
        if (editor == null) {
            result.put("status", "error");
            result.put("message", "Không phải Tanto Editor!");
            return result;
        }

        List<Mangaka> list = mangakaRepository.findByEditor_Id(editor.getId());
        List<Map<String, Object>> data = list.stream().map(m -> {
            Map<String, Object> map = new HashMap<>();
            map.put("mangakaId", m.getId());
            map.put("name", m.getUser().getFullname());
            map.put("email", m.getUser().getEmail());
            return map;
        }).collect(Collectors.toList());

        result.put("status", "success");
        result.put("data", data);
        return result;
    }

    @GetMapping("/mangaka/{mangakaId}/series")
    @ResponseBody
    public Map<String, Object> getMangakaSeries(@PathVariable String mangakaId, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập!");
            return result;
        }

        List<Series> list = seriesRepository.findByProposal_Mangaka_Id(mangakaId);
        List<Map<String, Object>> data = list.stream().map(s -> {
            Map<String, Object> map = new HashMap<>();
            map.put("seriesId", s.getId());
            map.put("seriesName", s.getSeriesName());
            map.put("status", s.getStatus());
            map.put("startDate", s.getStartDate());
            
            List<Chapter> chapters = chapterRepository.findBySeriesId(s.getId());
            int totalView = chapters.stream().mapToInt(Chapter::getViewCount).sum();
            map.put("viewCount", totalView);
            return map;
        }).collect(Collectors.toList());

        result.put("status", "success");
        result.put("data", data);
        return result;
    }

    @Operation(summary = "Tantou sửa deadline chapter chưa nộp (deadline phải là thứ 7)")
    @PostMapping("/chapters/{chapterId}/deadline")
    @ResponseBody
    public Map<String, Object> updateChapterDeadline(@PathVariable String chapterId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deadline,
            HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập!");
            return result;
        }

        Chapter chapter = chapterRepository.findById(chapterId).orElse(null);
        if (chapter == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy chapter: " + chapterId);
            return result;
        }

        if (!"unfinish".equals(chapter.getStatus())) {
            result.put("status", "error");
            result.put("message", "Chỉ có thể sửa deadline khi chapter chưa được nộp (trạng thái 'unfinish')!");
            return result;
        }

        if (deadline.getDayOfWeek() != DayOfWeek.SATURDAY) {
            result.put("status", "error");
            result.put("message", "Deadline phải là ngày thứ 7!");
            return result;
        }

        chapter.setDeadline(deadline);
        chapterRepository.save(chapter);

        result.put("status", "success");
        result.put("message", "Đã cập nhật deadline chapter '" + chapter.getChapterName() + "'!");
        return result;
    }
}