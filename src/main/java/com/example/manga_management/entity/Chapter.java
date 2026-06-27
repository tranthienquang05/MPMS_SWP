package com.example.manga_management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Column(name = "Chapternumber")
    private Integer chapterNumber;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "tantou_comment", length = 500)
    private String tantouComment;
}
