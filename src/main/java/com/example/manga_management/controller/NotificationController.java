package com.example.manga_management.controller;

import com.example.manga_management.entity.Notification;
import com.example.manga_management.entity.User;
import com.example.manga_management.repository.NotificationRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import java.util.List;

@ControllerAdvice
public class NotificationController {

    @Autowired
    private NotificationRepository noteRepo;

    @ModelAttribute
    public void addNotificationsToModel(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user != null) {
            // Lấy danh sách thông báo theo Role và ID của user đang login
            List<Notification> notifications = noteRepo.findByUserIdOrRoleOrderByCreatedAtDesc(user.getId(),
                    user.getRole());
            model.addAttribute("myNotifications", notifications);
            model.addAttribute("unreadCount", notifications.stream().filter(n -> !n.isRead()).count());
        }
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