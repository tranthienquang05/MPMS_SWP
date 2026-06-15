package com.example.manga_management.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "proposal")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Proposal {

    @Id
    @Column(name = "ProposalID", length = 6)
    private String id;

    @ManyToOne
    @JoinColumn(name = "MangakaID", nullable = false)
    private Mangaka mangaka;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "SeriesName", nullable = false, length = 30)
    private String seriesName;

<<<<<<< HEAD
    @Column(name = "FilePath", length = 30)
    private String filePath;
=======
    @Column(length = 30)
    private String content;

>>>>>>> 9f8b42adf0b12fdb920dcb3763566b75d35bf96c
}
