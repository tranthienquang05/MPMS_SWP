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
@RequestMapping("/mangaka")
public class MangakaController {

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"MANGAKA".equals(user.getRole())) return "redirect:/manga/login";

        model.addAttribute("allSeries", MangaData.seriesList);
        model.addAttribute("allChapters", MangaData.chapterList);
        return "mangaka-dashboard";
    }

    @PostMapping("/create-series")
    public String createSeries(@RequestParam String title, @RequestParam String description, HttpSession session) {
        MangaData.seriesList.add(new MangaSeries(MangaData.seriesIdCounter++, title, description, 101L, "DRAFT"));
        
        // Mẹo thông minh: Nếu người thực hiện không phải Mangaka (ví dụ từ trang dashboard tổng hợp), quay lại dashboard tổng hợp
        User user = (User) session.getAttribute("user");
        if (user != null && !"MANGAKA".equals(user.getRole())) {
            return "redirect:/manga/dashboard"; 
        }
        return "redirect:/mangaka/dashboard";
    }

    @PostMapping("/create-chapter")
    public String createChapter(@RequestParam Long seriesId, @RequestParam String title, @RequestParam int volumeNumber) {
        MangaData.chapterList.add(new Chapter(MangaData.chapterIdCounter++, seriesId, title, volumeNumber, "SKETCHING"));
        return "redirect:/mangaka/dashboard";
    }

    @PostMapping("/assign-task")
    public String assignTask(@RequestParam Long chapterId) {
        for (Chapter ch : MangaData.chapterList) {
            if (ch.getId().equals(chapterId)) { 
                ch.setStatus("ASSISTANT_WORKING"); // Khớp với trạng thái th:if="${ch.status == 'ASSISTANT_WORKING'}" của Assistant
                break; 
            }
        }
        return "redirect:/mangaka/dashboard";
    }

    @PostMapping("/fix-chapter/{id}")
    public String fixChapter(@PathVariable Long id) {
        for (Chapter ch : MangaData.chapterList) {
            if (ch.getId().equals(id)) { 
                ch.setStatus("PENDING_EDITOR_REVIEW"); // Khớp với màn hình duyệt của Editor
                break; 
            }
        }
        return "redirect:/mangaka/dashboard";
    }
}