package com.example.manga_management.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.manga_management.entity.Proposal;
import com.example.manga_management.repository.ProposalRepository;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/manga/tantou")
public class TantouController {

    private final ProposalRepository proposalRepository;

    public TantouController(ProposalRepository proposalRepository) {
        this.proposalRepository = proposalRepository;
    }

    @GetMapping("")
    public String tantouPage(HttpSession session, Model model) {
        if (session.getAttribute("user") == null) {
            return "redirect:/login";
        }

        List<Proposal> list = proposalRepository.findByStatus("finish");
        System.err.println(">>> DEBUG: SỐ LƯỢNG BẢN GHI TÌM THẤY LÀ: " + list.size());

        model.addAttribute("listProposals", list);
        return "tantou";
    }

    @PostMapping("/review")
    public String tantouReview(@RequestParam String id, @RequestParam String action) {
        Proposal p = proposalRepository.findById(id).orElse(null);
        if (p != null) {
            p.setStatus("yes".equals(action) ? "checked" : "unfinish");
            proposalRepository.save(p);
        }
        return "redirect:/manga/tantou";
    }

}
