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

    public BoardController(ProposalRepository proposalRepository, ProposalService proposalService) {
        this.proposalRepository = proposalRepository;
        this.proposalService = proposalService;
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

        String boardId = (String) session.getAttribute("boardId");

        if (id == null || id.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Dữ liệu dự án không hợp lệ!");
            return "redirect:/manga/editor";
        }

        try {
            proposalService.submitVote(id, action, boardId);
            redirectAttributes.addFlashAttribute("message", "Đã ghi nhận phiếu bầu!");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/manga/editor";
    }
}