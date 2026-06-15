package com.example.manga_management.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "mangaPage")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MangaPage {

    @Id
    @Column(name = "PageID", length = 7)
    private String id;

    @ManyToOne
    @JoinColumn(name = "ChapterID", nullable = false)
    private Chapter chapter;

    @Column(name = "FilePath", length = 30)
    private String filePath;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 255)
    private String feedback;
}
