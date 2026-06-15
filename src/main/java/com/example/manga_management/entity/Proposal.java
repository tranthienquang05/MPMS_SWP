package com.example.manga_management.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "proposal")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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

    @Column(length = 30)
    private String content;
}
