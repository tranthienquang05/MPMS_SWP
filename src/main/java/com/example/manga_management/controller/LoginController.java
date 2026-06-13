package com.example.manga_management.controller;

import com.example.manga_management.model.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/manga")
public class LoginController {

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String handleLogin(@RequestParam String username, @RequestParam String password, HttpSession session, Model model) {
        User user = null;

        if ("mangaka".equalsIgnoreCase(username)) {
            user = new User(101L, "mangaka", "Oda Eiichiro", "MANGAKA");
            session.setAttribute("user", user);
            return "redirect:/mangaka/dashboard";
        } else if ("assistant".equalsIgnoreCase(username)) {
            user = new User(201L, "assistant", "Trợ lý Kaito", "ASSISTANT");
            session.setAttribute("user", user);
            return "redirect:/assistant/dashboard";
        } else if ("editor".equalsIgnoreCase(username)) {
            user = new User(301L, "editor", "Biên tập viên Yamato", "EDITOR");
            session.setAttribute("user", user);
            return "redirect:/editor/dashboard";
        } else if ("board".equalsIgnoreCase(username)) {
            // Đồng bộ vai trò BOARD viết hoa thống nhất
            user = new User(401L, "board", "Trưởng hội đồng Shounen", "BOARD");
            session.setAttribute("user", user);
            return "redirect:/board/dashboard";
        }

        model.addAttribute("error", "Sai tên đăng nhập hoặc mật khẩu!");
        return "login";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/manga/login";
    }
}