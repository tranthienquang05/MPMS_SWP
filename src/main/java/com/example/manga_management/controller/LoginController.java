package com.example.manga_management.controller;

import com.example.manga_management.model.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/manga")
public class LoginController {

    @GetMapping({"/", "/login"})
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String handleLogin(@RequestParam String txtUsername, @RequestParam String txtPassword, HttpSession session, Model model) {
        User user = null;

 

        model.addAttribute("error", "Sai tên đăng nhập hoặc mật khẩu!");
        return "login";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/manga/login";
    }
}