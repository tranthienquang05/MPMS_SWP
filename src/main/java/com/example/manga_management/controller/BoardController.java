package com.example.manga_management.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.manga_management.entity.Proposal;
import com.example.manga_management.repository.ProposalRepository;
import com.example.manga_management.service.ProposalService;

@Controller
public class BoardController {
    private final ProposalRepository proposalRepository;
    private final ProposalService proposalService; // Thêm Service này

    public BoardController(ProposalRepository proposalRepository, ProposalService proposalService) {
        this.proposalRepository = proposalRepository;
        this.proposalService = proposalService;
    }

    @GetMapping("/board")
    public String boardPage(Model model) {
        List<Proposal> list = proposalRepository.findByStatus("checked");

        model.addAttribute("listProposals", list);

        return "board";
    }

    @PostMapping("/board/vote")
    public String boardReview(@RequestParam String id, @RequestParam String action) {
        proposalService.submitVote(id, action);
        return "redirect:/manga/board";
    }

}
