package com.example.manga_management.repository;

import com.example.manga_management.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    // Tìm thông báo dành riêng cho User hoặc dành cho cả Role
    List<Notification> findByUserIdOrRoleOrderByCreatedAtDesc(String userId, String role);

    long countByUserIdAndIsReadFalse(String userId);
}