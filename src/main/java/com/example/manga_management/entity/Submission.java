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
    private String submissionID;

    @ManyToOne
    @JoinColumn(name = "PageID", nullable = false)
    private MangaPage mangaPage;

    @ManyToOne
    @JoinColumn(name = "AssistantID", nullable = false)
    private Assistant assistant;

    @Column(name = "FilePath", length = 60)
    private String filePath;

    @Column(name = "comment", length = 1000)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false)
    private String status;
    
}
