package com.example.manga_management.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.manga_management.entity.User;

import jakarta.servlet.http.HttpSession;

@Controller
public class SpaController {

    @GetMapping({
        "/manga/mangaka/myseries/{seriesId}",
        "/manga/mangaka/myseries/{sid}/{cid}",
        "/manga/mangaka/myseries/{sid}/{cid}/{pid}/edit",
        "/manga/mangaka/submission/{id}/edit"
    })
    public String mangakaApp(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        return "mangaka";
    }
}
