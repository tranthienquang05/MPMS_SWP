package com.example.manga_management.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Column(name = "Status", nullable = false, length = 20)
    private String status;

    @Column(name = "SeriesName", nullable = false, length = 30)
    private String seriesName;

    @Column(name = "FilePath", length = 60)
    private String filePath;
    
    @OneToOne(mappedBy = "proposal", cascade = CascadeType.ALL)
    private Series series;
}
