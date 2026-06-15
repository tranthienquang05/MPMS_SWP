package com.example.manga_management.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "voteResult")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VoteResult {

    @Id
    @Column(name = "VoteID", length = 7)
    private String id;

    @ManyToOne
    @JoinColumn(name = "SeriesID", nullable = false)
    private Series series;

    @Column(name = "VoteNumber", nullable = false)
    private Integer voteNumber;

    @Column(nullable = false)
    private Integer month;

    @Column(nullable = false)
    private Integer year;
}
