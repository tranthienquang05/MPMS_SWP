package com.example.manga_management.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "voteSession")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VoteSession {

    @Id
    @Column(name = "SessionID", length = 8)
    private String id;

    @ManyToOne
    @JoinColumn(name = "SeriesID", nullable = false)
    private Series series;

    @ManyToOne
    @JoinColumn(name = "CreatedByBoardID", nullable = true)
    private Board createdBy;

    @Column(name = "VoteType", nullable = false, length = 10)
    private String voteType; // "stop" or "reward"

    @Column(name = "Status", nullable = false, length = 10)
    private String status; // "active" or "closed"

    @Column(name = "CreatedAt", nullable = false)
    private LocalDate createdAt;

    @Column(name = "AutoCreated", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean autoCreated = false;

    @Column(name = "Reason", length = 500)
    private String reason;

    @Column(name = "DefenseFilePath", length = 100)
    private String defenseFilePath;

    @Column(name = "DefenseNote", length = 500)
    private String defenseNote;
}
