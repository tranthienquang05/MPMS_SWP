package com.example.manga_management.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "chapter")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Chapter {

    @Id
    @Column(name = "ChapterID", length = 7)
    private String id;

    @ManyToOne
    @JoinColumn(name = "SeriesID", nullable = false)
    private Series series;

    @Column(name = "Chaptername", nullable = false, length = 30)
    private String chapterName;

    @Column(name = "Volnumber")
    private Integer volNumber;

    @Column(nullable = false, length = 20)
    private String status;
}
