package com.example.manga_management.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "series")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Series {

    @Id
    @Column(name = "SeriesID", length = 6)
    private String id;

    @ManyToOne
    @JoinColumn(name = "ProposalID", nullable = false)
    private Proposal proposal;

    @Column(name = "BookJacket", length = 30)
    private String bookJacket;

    @Column(name = "SeriesName", nullable = false, length = 30)
    private String seriesName;

    @Column(name = "Description", length = 255)
    private String description;

    @Column(name = "StartDate")
    private LocalDate startDate;

    @Column(name = "Status", nullable = false, length = 20)
    private String status;
}