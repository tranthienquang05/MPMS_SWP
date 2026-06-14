package com.example.manga_management.controller;

import com.example.manga_management.entity.User;
import com.example.manga_management.service.UserService;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/manga")
public class LoginController {

    private final UserService userService;

    public LoginController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping({ "/", "/login" })
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

        switch (user.getRole()) {
            case "Board":
                return "redirect:/manga/tantou";

            case "Editor":
                return "redirect:/manga/editor";

            case "Mangaka":
                return "redirect:/manga/mangaka";

            case "Assistant":
                return "redirect:/manga/assistant";

            default:
                model.addAttribute("error", "Vai trò người dùng không hợp lệ!");
                return "login";
        }
    }

    @GetMapping("/tantou")
    public String tantouPage() {
        return "tantou";
    }

    @GetMapping("/editor")
    public String editorPage() {
        return "editor";
    }

    @GetMapping("/mangaka")
    public String mangakaPage() {
        return "mangaka";
    }

    @GetMapping("/assistant")
    public String assistantPage() {
        return "assistant";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/manga/login";
    }
}