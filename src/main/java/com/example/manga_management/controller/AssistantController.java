package com.example.manga_management.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.example.manga_management.entity.User;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/manga/assistant")
public class AssistantController {
     @GetMapping("")
    public String assistantPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        
        return "assistant";
    }
}
