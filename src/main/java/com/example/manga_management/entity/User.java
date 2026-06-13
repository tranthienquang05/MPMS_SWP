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
    @Column(length = 6)
    private String id;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, length = 30)
    private String fullname;

    @Column(nullable = false, unique = true, length = 30)
    private String username;

    @Column(nullable = false, unique = true, length = 30)
    private String email;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(length = 30)
    private String avatar;

    @Column(length = 255)
    private String profile;
}