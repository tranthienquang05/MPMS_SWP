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

    @Column(name = "Pagenumber")
    private Integer pageNumber;

    @Column(name = "FilePath", length = 60)
    private String filePath;

    @Column(name = "Status", nullable = false, length = 20)
    private String status;

    @Column(name = "PageType", length = 20)
    private String pageType;

    @Column(name = "Script", length = 1000)
    private String script;

}
