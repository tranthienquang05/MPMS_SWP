package com.example.manga_management.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "submission")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Submission {

    @Id
    @Column(name = "SubmissionID", length = 7)
    private String id;

    @ManyToOne
    @JoinColumn(name = "PageID", nullable = false)
    private MangaPage page;

    @ManyToOne
    @JoinColumn(name = "AssistantID", nullable = false)
    private Assistant assistant;

    @Column(name = "FilePath", length = 30)
    private String filePath;
}
