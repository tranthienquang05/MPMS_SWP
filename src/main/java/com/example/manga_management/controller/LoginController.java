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
@RequestMapping("/login")
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

    @GetMapping("")
    public String loginPage() {
        return "login";
    }

    @PostMapping("")
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
            case "tantor":
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

    

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}