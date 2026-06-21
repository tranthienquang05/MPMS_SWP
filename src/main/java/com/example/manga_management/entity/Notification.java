package com.example.manga_management.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "NotificationID")
    private Long id;

    @Column(name = "Content", nullable = false, length = 255)
    private String content;

    @Column(name = "Role", length = 20)
    private String role; 

    @Column(name = "UserID", length = 20)   
    private String userId; 

    @Column(name = "Link", length = 255)
    private String link; // Đường dẫn khi click vào thông báo

    @Column(name = "IsRead", nullable = false)
    private boolean isRead = false;

    @Column(name = "CreatedAt", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Bạn có thể thêm @ManyToOne với bảng User nếu muốn quản lý quan hệ chặt chẽ
}