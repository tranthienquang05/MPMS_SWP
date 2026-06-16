package com.example.manga_management.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.manga_management.entity.Proposal;
import com.example.manga_management.repository.ProposalRepository;

@Controller
public class BoardController {
    private final ProposalRepository proposalRepository;

    public BoardController(ProposalRepository proposalRepository) {
        this.proposalRepository = proposalRepository;
    }

    @PostMapping("/board/review")
    public String boardReview(@RequestParam String id, @RequestParam String action) {
        Proposal p = proposalRepository.findById(id).orElse(null);
        if (p != null) {
            // Yes: Pass dự án. No: Loại bỏ dự án (reject)
            p.setStatus("yes".equals(action) ? "pass" : "reject");
            proposalRepository.save(p);
        }
        return "redirect:/manga/board";
    }

}
