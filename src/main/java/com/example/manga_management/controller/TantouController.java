package com.example.manga_management.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.example.manga_management.entity.Proposal;
import com.example.manga_management.entity.TantoEditor;
import com.example.manga_management.entity.User;
import com.example.manga_management.repository.ProposalRepository;
import com.example.manga_management.repository.TantoEditorRepository;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/manga/tantou")
public class TantouController {

    private final ProposalRepository proposalRepository;
    private final TantoEditorRepository tantoEditorRepository;
    private final NotificationController notificationController;

    public TantouController(ProposalRepository proposalRepository, TantoEditorRepository tantoEditorRepository,
            NotificationController notificationController) {
        this.proposalRepository = proposalRepository;
        this.tantoEditorRepository = tantoEditorRepository;
        this.notificationController = notificationController;
    }

    @GetMapping("")
    public String tantouPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null)
            return "redirect:/login";

        TantoEditor editor = tantoEditorRepository.findByUser(user).orElse(null);
        if (editor == null)
            return "redirect:/login";

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
            @RequestParam(required = false) Integer score,
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
                notificationController.send(null, p.getMangaka().getUser().getId(),
                        "Đề xuất '" + p.getSeriesName() + "' đã được Tantou duyệt.",
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

                notificationController.send(null, p.getMangaka().getUser().getId(),
                        "Bản thảo '" + p.getSeriesName() + "' cần sửa. Hạn: " + deadline,
                        "/manga/mangaka/my-projects");
            }
            case "reject" -> {
                p.setStatus("locked");
                notificationController.send(null, p.getMangaka().getUser().getId(),
                        "Đề xuất '" + p.getSeriesName() + "' đã bị từ chối.",
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
}