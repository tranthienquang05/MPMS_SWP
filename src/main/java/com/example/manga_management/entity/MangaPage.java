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

    @Column(name = "FilePath", length = 60)
    private String filePath;

   
    
}
