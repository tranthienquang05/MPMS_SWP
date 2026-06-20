package com.example.manga_management.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "SeriesVote")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SeriesVote {

    @Id
    @Column(name = "SvoteID", length = 6)
    private String ID;

    @ManyToOne
    @JoinColumn(name = "SeriesID", nullable = false)
    private Series series;

    @ManyToOne
    @JoinColumn(name = "BoardID", nullable = false)
    private Board board;


    @Column(name = "Vote", nullable = false, length = 10)
    private String vote;

    @Column(name = "VoteDate", nullable = false)
    private LocalDate voteDate;
}