package com.example.manga_management.repository;

import com.example.manga_management.entity.SeriesVote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeriesVoteRepository extends JpaRepository<SeriesVote, String> {
    long countBySeries_Id(String seriesId);

    // Lỗi cũ: countBySeries_IdAndVote
    // Sửa đúng (viết gọn):
    long countBySeries_IdAndVote(String id, String vote);

    // Lỗi cũ: countBySeriesIDAndBoardID
    // Sửa đúng:
    long countBySeries_IdAndBoard_Id(String seriesId, String boardId);

}