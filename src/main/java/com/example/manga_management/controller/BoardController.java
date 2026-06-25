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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.manga_management.entity.Proposal;
import com.example.manga_management.repository.ProposalRepository;
import com.example.manga_management.service.ProposalService;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/manga/editor")
public class BoardController {
    private final ProposalRepository proposalRepository;
    private final ProposalService proposalService;
    private final NotificationController notificationController;

    public BoardController(ProposalRepository proposalRepository, ProposalService proposalService,
            NotificationController notificationController) {
        this.proposalRepository = proposalRepository;
        this.proposalService = proposalService;
        this.notificationController = notificationController;
    }

    @Operation(summary = "Xem danh sách bản thảo chờ bỏ phiếu")
    @GetMapping("")
    public String editorPage(HttpSession session) {
        if (session.getAttribute("user") == null)
            return "redirect:/login";
        return "editor";
    }

    @Operation(summary = "Xem danh sách bản thảo chờ bỏ phiếu")
    @GetMapping("/data")
    @ResponseBody
    public Map<String, Object> boardData(HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        Object userObj = session.getAttribute("user");
        if (userObj == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập!");
            return result;
        }

        com.example.manga_management.entity.User user = (com.example.manga_management.entity.User) userObj;

        List<Proposal> list = proposalRepository.findByStatus("checked");
        result.put("status", "success");
        result.put("userId", user.getId());
        result.put("total", list.size());
        result.put("proposals", list);
        return result;
    }

    @Operation(summary = "Bỏ phiếu cho bản thảo")
    @PostMapping("/vote")
    @ResponseBody
    public Map<String, String> boardReview(
            @RequestParam String id,
            @RequestParam String action,
            @RequestParam String userId) {

        Map<String, String> result = new HashMap<>();

        if (id == null || id.isEmpty()) {
            result.put("status", "error");
            result.put("message", "ID dự án không hợp lệ!");
            return result;
        }

        try {
            proposalService.submitVote(id, action, userId);

            Proposal p = proposalRepository.findById(id).orElse(null);
            if (p != null) {
                String voteResult = "pass".equals(action) ? "Thông qua" : "Từ chối";

                notificationController.send(null, p.getMangaka().getUser().getId(),
                        "Dự án '" + p.getSeriesName() + "' đã nhận phiếu: " + voteResult,
                        "/manga/mangaka/my-projects");

                notificationController.send("tantou", null,
                        "Dự án '" + p.getSeriesName() + "' đã nhận phiếu: " + voteResult,
                        "/manga/tantou");

                result.put("status", "success");
                result.put("proposalId", id);
                result.put("currentStatus", p.getStatus());
                result.put("message", "Đã ghi nhận phiếu bầu: " + voteResult);
            }

        } catch (RuntimeException e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }
}