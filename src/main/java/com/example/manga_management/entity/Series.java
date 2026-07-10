package com.example.manga_management.entity;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "series")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"proposal"})
public class Series {

    @Id
    @Column(name = "SeriesID", length = 6)
    private String id;

    @OneToOne
    @JoinColumn(name = "ProposalID", nullable = false)
    private Proposal proposal;

    @Column(name = "BookJacket", length = 30)
    private String bookJacket;

    @Column(name = "SeriesName", nullable = false, length = 30)
    private String seriesName;

    @Column(name = "Description", length = 255)
    private String description;

    @Column(name = "Genre", length = 50)
    private String genre;

    @Column(name = "StartDate")
    private LocalDate startDate;

    @Column(name = "Status", nullable = false, length = 20)
    private String status;
}