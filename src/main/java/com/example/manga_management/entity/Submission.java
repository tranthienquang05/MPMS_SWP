package com.example.manga_management.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

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

    @Column(name = "Deadline", nullable = false, columnDefinition = "DATETIME")
    private LocalDateTime deadline;

    @Column(name = "FilePath", length = 60)
    private String filePath;

    @Column(name = "comment", length = 1000)
    private String comment;

    @Column(name = "Status", nullable = false, length = 20)
    private String status;

    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;
}
