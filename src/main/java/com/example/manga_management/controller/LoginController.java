package com.example.manga_management.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.manga_management.entity.User;
import com.example.manga_management.repository.ProposalRepository;
import com.example.manga_management.repository.TantoEditorRepository;
import com.example.manga_management.service.UserService;

import jakarta.servlet.http.HttpSession;

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
            model.addAttribute("error", "Sai thông tin đăng nhập");
            return "login";
        }

        session.setAttribute("user", user);

        switch (user.getRole().toLowerCase()) {
            case "board":
                return "redirect:/manga/editor";
            case "tantou":
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