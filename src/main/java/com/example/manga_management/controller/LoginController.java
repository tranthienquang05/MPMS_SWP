package com.example.manga_management.controller;

import com.example.manga_management.entity.Proposal;
import com.example.manga_management.entity.User;
import com.example.manga_management.repository.ProposalRepository;
import com.example.manga_management.repository.TantoEditorRepository;
import com.example.manga_management.service.UserService;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/manga")
public class LoginController {

    private final UserService userService;
    private final ProposalRepository proposalRepository;
    private final TantoEditorRepository tantoEditorRepository;

    public LoginController(UserService userService, 
                           ProposalRepository proposalRepository, 
                           TantoEditorRepository tantoEditorRepository) {
        this.userService = userService;
        this.proposalRepository = proposalRepository;
        this.tantoEditorRepository = tantoEditorRepository;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String handleLogin(
            @RequestParam String txtUsername,
            @RequestParam String txtPassword,
            HttpSession session,
            Model model) {
        User user = userService.login(txtUsername, txtPassword);

        if (user == null) {
            model.addAttribute("error", "Sai tên đăng nhập hoặc mật khẩu!");
            return "login";
        }

        session.setAttribute("user", user);

        switch (user.getRole().toLowerCase()) {
            case "board":
                return "redirect:/manga/editor";
            case "editor":
                return "redirect:/manga/tantou";
            case "mangaka":
                return "redirect:/manga/mangaka";
            case "assistant":
                return "redirect:/manga/assistant";
            default:
                model.addAttribute("error", "Vai trò người dùng không hợp lệ!");
                return "login";
        }
    }

    @GetMapping("/tantou")
    public String tantouPage(HttpSession session, Model model) {
        if (session.getAttribute("user") == null) return "redirect:/manga/login";
        model.addAttribute("pendingProposals", proposalRepository.findByStatus("pending"));
        return "tantou";
    }

    @GetMapping("/tantou/approve")
    public String handleTantouApprove(@RequestParam String id) {
        Proposal proposal = proposalRepository.findById(id).orElse(null);
        if (proposal != null) {
            proposal.setStatus("approved_by_tantou");
            proposalRepository.save(proposal);
        }
        return "redirect:/manga/tantou";
    }

    @GetMapping("/editor")
    public String editorPage(HttpSession session, Model model) {
        if (session.getAttribute("user") == null) return "redirect:/manga/login";
        model.addAttribute("votingProposals", proposalRepository.findByStatus("approved_by_tantou"));
        return "editor";
    }

    @GetMapping("/editor/approve")
    public String handleEditorApprove(@RequestParam String id) {
        Proposal proposal = proposalRepository.findById(id).orElse(null);
        if (proposal != null) {
            proposal.setStatus("publishing_approved");
            proposalRepository.save(proposal);
        }
        return "redirect:/manga/editor";
    }

    @GetMapping("/editor/reject")
    public String handleEditorReject(@RequestParam String id) {
        Proposal proposal = proposalRepository.findById(id).orElse(null);
        if (proposal != null) {
            proposal.setStatus("rejected");
            proposalRepository.save(proposal);
        }
        return "redirect:/manga/editor";
    }

    @GetMapping("/assistant")
    public String assistantPage(HttpSession session) {
        if (session.getAttribute("user") == null) return "redirect:/manga/login";
        return "assistant";
    }

    @GetMapping("/view-file")
    @ResponseBody
    public org.springframework.core.io.Resource serveFile(@RequestParam("path") String path) throws java.net.MalformedURLException {
        String cleanPath = path.replace("/uploads/", "");
        java.nio.file.Path fileLocation = java.nio.file.Paths.get("D:/LEARNING/SWP391/uploads/").resolve(cleanPath);
        return new org.springframework.core.io.UrlResource(fileLocation.toUri());
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/manga/login";
    }
}