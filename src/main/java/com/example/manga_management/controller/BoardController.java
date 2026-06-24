package com.example.manga_management.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.manga_management.entity.Proposal;
import com.example.manga_management.repository.ProposalRepository;
import com.example.manga_management.service.ProposalService;

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

    @GetMapping("")
    public String boardPage(HttpSession session, Model model) {
        // Kiểm tra đăng nhập theo cách đã chạy ổn ở Tantou
        if (session.getAttribute("user") == null) {
            return "redirect:/login";
        }

        List<Proposal> list = proposalRepository.findByStatus("checked");
        model.addAttribute("listProposals", list);
        return "editor";
    }

    @PostMapping("/vote")
    public String boardReview(@RequestParam String id,
            @RequestParam String action,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        // 1. Lấy đối tượng User từ session (key là "user")
        Object userObj = session.getAttribute("user");

        // 2. Kiểm tra nếu chưa đăng nhập
        if (userObj == null) {
            return "redirect:/login";
        }

        // 3. Ép kiểu an toàn từ Object sang User
        com.example.manga_management.entity.User user = (com.example.manga_management.entity.User) userObj;

        // 4. Lấy ID từ đối tượng user vừa ép kiểu
        String userId = user.getId();

        try {
            // Kiểm tra ID dự án
            if (id == null || id.isEmpty()) {
                throw new RuntimeException("Dữ liệu ID dự án không hợp lệ!");
            }

            // Gọi service với userId (String) đã lấy được
            proposalService.submitVote(id, action, userId);
            Proposal p = proposalRepository.findById(id).orElse(null);
            if (p != null) {
                String result = "pass".equals(action) ? "Thông qua" : "Từ chối";

                // 1. Thông báo cho Mangaka
                notificationController.send(null, p.getMangaka().getUser().getId(),
                        "Dự án '" + p.getSeriesName() + "'đã nhận được từ hội đồng một phiếu: " + result,
                        "/manga/mangaka/my-projects");
                // 2. Thông báo cho Tanto nếu dự án bị từ chối

                notificationController.send("tantou", null,
                        "Dự án '" + p.getSeriesName() + "' mà bạn duyệt đã nhận được từ hội đồng một phiếu:" + result,
                        "/manga/tantou");
            }
            redirectAttributes.addFlashAttribute("message", "Đã ghi nhận phiếu bầu!");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/manga/editor";
    }
}