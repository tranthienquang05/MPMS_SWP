package com.example.manga_management.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @Column(name = "ID", length = 6)
    private String id;

    @Column(name = "Role", nullable = false, length = 20)
    private String role;

    @Column(name = "Fullname", nullable = false, length = 30)
    private String fullname;

    @Column(name = "Username", nullable = false, unique = true, length = 30)
    private String username;

    @Column(name = "Email", nullable = false, unique = true, length = 50)
    private String email;

    @Column(name = "Password", nullable = false, length = 30)
    private String password;

    @Column(name = "Avatar", length = 60)
    private String avatar;

    @Column(name = "Profile", length = 255)
    private String profile;

    @Column(name = "EmailVerified", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean emailVerified = false;

    @Column(name = "Phone", length = 20)
    private String phone;

    @Column(name = "SocialLinks", length = 500)
    private String socialLinks;
}