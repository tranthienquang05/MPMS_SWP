package com.example.manga_management.controller;

import com.example.manga_management.model.MangaData;
import com.example.manga_management.model.MangaSeries;
import com.example.manga_management.model.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/board")
public class BoardController {

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"BOARD".equals(user.getRole())) return "redirect:/manga/login";

        model.addAttribute("allSeries", MangaData.seriesList);
        return "board-dashboard";
    }

    @PostMapping("/vote-series/{id}")
    public String voteSeries(@PathVariable Long id, @RequestParam boolean isFinalApproved) {
        for (MangaSeries s : MangaData.seriesList) {
            if (s.getId().equals(id)) {
                s.setStatus(isFinalApproved ? "APPROVED" : "REJECTED");
                break;
            }
        }
        return "redirect:/board/dashboard";
    }
}