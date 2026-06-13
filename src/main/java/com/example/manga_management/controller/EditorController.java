package com.example.manga_management.controller;

import com.example.manga_management.model.MangaSeries;
import com.example.manga_management.model.Chapter;
import com.example.manga_management.model.MangaData;
import com.example.manga_management.model.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/editor")
public class EditorController {

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null || ! "EDITOR".equals(user.getRole())) return "redirect:/manga/login";

        model.addAttribute("allSeries", MangaData.seriesList);
        model.addAttribute("allChapters", MangaData.chapterList);
        return "editor-dashboard"; // Đã sửa tên file tương ứng (.html)
    }

    @PostMapping("/review-draft/{id}")
    public String reviewDraft(@PathVariable Long id, @RequestParam String action) {
        for (MangaSeries s : MangaData.seriesList) {
            if (s.getId().equals(id)) {
                s.setStatus("approve".equals(action) ? "VOTING" : "REJECTED");
                break;
            }
        }
        return "redirect:/editor/dashboard";
    }

    @PostMapping("/review-chapter/{id}")
    public String reviewChapter(@PathVariable Long id, @RequestParam String action) {
        for (Chapter ch : MangaData.chapterList) {
            if (ch.getId().equals(id)) {
                // Khớp trạng thái với giao diện: Duyệt -> PUBLIC, Từ chối -> AMENDMENT_REQUIRED
                ch.setStatus("approve".equals(action) ? "PUBLIC" : "AMENDMENT_REQUIRED");
                break;
            }
        }
        return "redirect:/editor/dashboard";
    }
}