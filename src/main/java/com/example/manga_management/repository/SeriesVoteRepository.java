package com.example.manga_management.repository;

import com.example.manga_management.entity.SeriesVote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeriesVoteRepository extends JpaRepository<SeriesVote, String> {
    long countBySeries_SeriesID(String seriesID);

    long countBySeries_SeriesIDAndVote(String seriesID, String vote);
    
    long countBySeries_SeriesIDAndBoard_BoardID(
        String seriesID,
        String boardID
        );
}
