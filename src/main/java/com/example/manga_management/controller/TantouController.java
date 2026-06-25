package com.example.manga_management.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

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

    private NotificationController notificationController;

    public TantouController(ProposalRepository proposalRepository, TantoEditorRepository tantoEditorRepository,
            NotificationController notificationController) {
        this.proposalRepository = proposalRepository;
        this.tantoEditorRepository = tantoEditorRepository;
        this.notificationController = notificationController;
    }

    @Operation(summary = "Xem danh sách bản thảo chờ duyệt")
    @GetMapping("")
    public String tantouPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null)
            return "redirect:/login";

        TantoEditor editor = tantoEditorRepository.findByUser(user).orElse(null);
        if (editor == null)
            return "redirect:/login";

        // Chỉ truyền tantouId để JS dùng — không truyền listProposals nữa
        model.addAttribute("tantouId", editor.getId());
        return "tantou";
    }

    @Operation(summary = "Lấy danh sách bản thảo chờ duyệt theo tantouId")
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

        List<Proposal> list = proposalRepository
                .findByStatusAndMangaka_Editor_Id("finish", editor.getId());

        result.put("status", "success");
        result.put("total", list.size());
        result.put("proposals", list);
        return result;
    }

    @Operation(summary = "Duyệt hoặc từ chối bản thảo")
    @PostMapping("/review")
    @ResponseBody
    public Map<String, String> tantouReview(
            @RequestParam String id,
            @RequestParam String action,
            @RequestParam(required = false) String comment) {

        Map<String, String> result = new HashMap<>();

        Proposal p = proposalRepository.findById(id).orElse(null);
        if (p == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy đề xuất: " + id);
            return result;
        }

        p.setStatus("yes".equals(action) ? "checked" : "unfinish");
        if (comment != null && !comment.isBlank()) {
            p.setComment(comment.trim());
        }
        proposalRepository.save(p);

        String statusMsg = "yes".equals(action) ? "đã được duyệt" : "bị từ chối";
        String notifMsg = "Dự án '" + p.getSeriesName() + "' " + statusMsg
                + (comment != null && !comment.isBlank() ? ". Nhận xét: " + comment : "");

        notificationController.send(null, p.getMangaka().getUser().getId(),
                notifMsg, "/manga/mangaka/my-projects");

        if ("yes".equals(action)) {
            notificationController.send("board", null,
                    "Có dự án mới '" + p.getSeriesName() + "' cần bỏ phiếu!", "/manga/editor");
        }

        result.put("status", "success");
        result.put("proposalId", id);
        result.put("newStatus", p.getStatus());
        result.put("message", "Dự án " + statusMsg);
        return result;
    }

}
