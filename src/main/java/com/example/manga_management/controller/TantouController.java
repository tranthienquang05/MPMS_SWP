package com.example.manga_management.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.manga_management.entity.Proposal;
import com.example.manga_management.entity.TantoEditor;
import com.example.manga_management.entity.User;
import com.example.manga_management.repository.ProposalRepository;
import com.example.manga_management.repository.TantoEditorRepository;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/manga/tantou")
public class TantouController {

    private final ProposalRepository proposalRepository;
    private final TantoEditorRepository tantoEditorRepository;

    public TantouController(ProposalRepository proposalRepository, TantoEditorRepository tantoEditorRepository) {
        this.proposalRepository = proposalRepository;
        this.tantoEditorRepository = tantoEditorRepository;
    }

    @GetMapping("")
    public String tantouPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        TantoEditor editor = tantoEditorRepository.findByUser(user).orElse(null);

        List<Proposal> list = proposalRepository.findByStatusAndMangaka_Editor_Id("finish", editor.getId());
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
