package com.example.manga_management.controller;

import com.example.manga_management.model.Chapter;
import com.example.manga_management.model.MangaData;
import com.example.manga_management.model.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/assistant")
public class AssistantController {

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null || ! "ASSISTANT".equals(user.getRole())) return "redirect:/manga/login";

        model.addAttribute("allChapters", MangaData.chapterList);
        return "assistant-dashboard";
    }

    @PostMapping("/complete-task/{id}")
    public String completeTask(@PathVariable Long id) {
        for (Chapter ch : MangaData.chapterList) {
            if (ch.getId().equals(id)) { 
                // Trợ lý làm xong đẩy lên trạng thái chờ Editor duyệt (PENDING_EDITOR_REVIEW)
                ch.setStatus("PENDING_EDITOR_REVIEW"); 
                break; 
            }
        }
        return "redirect:/assistant/dashboard";
    }
}