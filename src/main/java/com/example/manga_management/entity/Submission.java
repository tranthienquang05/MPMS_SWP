package com.example.manga_management.entity;

import java.time.LocalDate;

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
    private MangaPage pageId;

    @ManyToOne
    @JoinColumn(name = "AssistantID", nullable = false)
    private Assistant assistant;

    @Column(name = "Deadline", nullable = false)
    private LocalDate deadline;

    @Column(name = "FilePath", length = 60)
    private String filePath;

    @Column(name = "comment", length = 1000)
    private String comment;

    @Column(name = "Status", nullable = false, length = 20)
    private String status;



}
