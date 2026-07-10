package com.example.manga_management.controller;

import com.example.manga_management.entity.Notification;
import com.example.manga_management.entity.User;
import com.example.manga_management.repository.NotificationRepository;
import com.example.manga_management.service.NotificationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@ControllerAdvice
public class NotificationController {

    @Autowired
    private NotificationRepository noteRepo;

    @Autowired
    private NotificationService notificationService;

    @ModelAttribute
    public void addNotificationsToModel(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user != null) {
            List<Notification> notifications = noteRepo
                    .findInbox(user.getId(), user.getRole())
                    .stream()
                    .filter(n -> n != null && n.getContent() != null)
                    .collect(java.util.stream.Collectors.toList());
            model.addAttribute("myNotifications", notifications);
            model.addAttribute("unreadCount", notifications.stream().filter(n -> !n.isRead()).count());
        }
    }

    @PostMapping("/notification/{id}/read")
    @ResponseBody
    public Map<String, Object> markOneRead(@PathVariable Long id, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            return result;
        }
        boolean ok = notificationService.markRead(id, user);
        result.put("status", ok ? "success" : "error");
        return result;
    }

    @PostMapping("/notification/read-all")
    @ResponseBody
    public Map<String, Object> markAllRead(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            return result;
        }
        List<Notification> list = noteRepo.findInbox(
                user.getId(), user.getRole());
        list.forEach(n -> n.setRead(true));
        noteRepo.saveAll(list);
        result.put("status", "success");
        return result;
    }

    public void send(String role, String userId, String content, String link) {
        Notification note = Notification.builder()
                .role(role)
                .userId(userId)
                .content(content)
                .link(link)
                .isRead(false)
                .build();
        noteRepo.save(note);
    }
}
