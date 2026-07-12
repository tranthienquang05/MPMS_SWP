package com.example.manga_management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "proposal") // Tên bảng trong DB
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Proposal {

    @Id
    @Column(name = "ProposalID", length = 6, nullable = false)
    private String id;

    // Quan hệ ManyToOne với Mangaka
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MangakaID", nullable = false)
    private Mangaka mangaka;

    @Column(name = "Status", nullable = false, length = 20)
    private String status;

    @Column(name = "SeriesName", nullable = false, length = 30)
    private String seriesName;

    @Column(name = "Genre", length = 50)
    private String genre;

    @Column(name = "FilePath", length = 60)
    private String filePath;

    @Column(name = "FileOfTantou", length = 60)
    private String fileOfTantou;

    @Column(name = "Comment", length = 1000)
    private String comment;

    @Column(name = "EditorScore")
    private Double editorScore;

    @Column(name = "RevisionDeadline")
    private LocalDateTime revisionDeadline;

    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;

    /** Thời điểm tantou duyệt / yêu cầu sửa / từ chối đề xuất này. */
    @Column(name = "ReviewedAt")
    private LocalDateTime reviewedAt;

    // Quan hệ OneToOne với Series
    // mappedBy trỏ tới tên biến 'proposal' trong entity Series
    @OneToOne(mappedBy = "proposal", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Series series;
}