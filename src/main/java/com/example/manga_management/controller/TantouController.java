package com.example.manga_management.controller;

import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.example.manga_management.entity.Proposal;
import com.example.manga_management.repository.ProposalRepository;
import jakarta.servlet.http.HttpSession;

@Controller
public class TantouController {

    private final ProposalRepository proposalRepository;

    public TantouController(ProposalRepository proposalRepository) {
        this.proposalRepository = proposalRepository;
    }

    @GetMapping("/tantou")
    public String tantouPage(HttpSession session, Model model) {
        // Kiểm tra login
        if (session.getAttribute("user") == null)
            return "redirect:/manga/login";

        List<Proposal> list = proposalRepository.findByStatus("unfinish");

        model.addAttribute("listProposals", list);

        return "tantou";
    }

}
