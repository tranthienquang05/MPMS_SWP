package com.example.manga_management.repository;

import com.example.manga_management.entity.SeriesVote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeriesVoteRepository extends JpaRepository<SeriesVote, String> {
    long countBySeries_Id(String seriesId);

    long countBySeries_IdAndVote(String seriesId, String vote);

    long countBySeries_IdAndBoard_Id(String seriesId, String boardId);
}
