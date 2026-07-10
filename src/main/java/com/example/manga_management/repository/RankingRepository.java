package com.example.manga_management.repository;

import com.example.manga_management.entity.LikeResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface RankingRepository extends JpaRepository<LikeResult, String> {

    @Query("SELECT s.id, s.seriesName, COALESCE(SUM(v.likeNumber), 0L) " +
           "FROM Series s LEFT JOIN LikeResult v ON v.series.id = s.id AND v.year = :year " +
           "WHERE s.status <> 'stopped' " +
           "GROUP BY s.id, s.seriesName " +
           "ORDER BY COALESCE(SUM(v.likeNumber), 0L) DESC")
    List<Object[]> findRankingByYear(@Param("year") int year);

    @Query("SELECT s.id, s.seriesName, COALESCE(v.likeNumber, 0) " +
           "FROM Series s LEFT JOIN LikeResult v ON v.series.id = s.id AND v.month = :month AND v.year = :year " +
           "WHERE s.status <> 'stopped' " +
           "ORDER BY COALESCE(v.likeNumber, 0) DESC")
    List<Object[]> findRankingByMonthAndYear(@Param("month") int month,
                                              @Param("year") int year);

    @Query("SELECT s.id, s.seriesName, COALESCE(SUM(v.likeNumber), 0L) " +
           "FROM Series s LEFT JOIN LikeResult v ON v.series.id = s.id AND v.year = :year " +
           "WHERE s.status <> 'stopped' " +
           "GROUP BY s.id, s.seriesName " +
           "ORDER BY COALESCE(SUM(v.likeNumber), 0L) ASC")
    List<Object[]> findBottomByYear(@Param("year") int year);

    @Query("SELECT s.id, s.seriesName, COALESCE(SUM(v.likeNumber), 0L) " +
           "FROM Series s LEFT JOIN LikeResult v ON v.series.id = s.id " +
           "AND v.year = :year AND v.month IN :months " +
           "WHERE s.status <> 'stopped' " +
           "GROUP BY s.id, s.seriesName " +
           "ORDER BY COALESCE(SUM(v.likeNumber), 0L) ASC")
    List<Object[]> findBottomByQuarterAndYear(@Param("months") List<Integer> months,
                                              @Param("year") int year);

    @Query("SELECT s.id, s.seriesName, COALESCE(v.likeNumber, 0) " +
           "FROM Series s LEFT JOIN LikeResult v ON v.series.id = s.id AND v.month = :month AND v.year = :year " +
           "WHERE s.status <> 'stopped' " +
           "ORDER BY COALESCE(v.likeNumber, 0) ASC")
    List<Object[]> findBottomByMonthAndYear(@Param("month") int month,
                                             @Param("year") int year);

    // ===== Queries for manual vote sessions (dùng SeriesVote cho cả 2 loại) =====

    @Query("SELECT s.id, s.seriesName, COALESCE(SUM(v.likeNumber), 0L) " +
           "FROM Series s LEFT JOIN LikeResult v ON v.series.id = s.id " +
           "AND v.year = :year AND v.month IN :months " +
           "WHERE s.status <> 'stopped' " +
           "GROUP BY s.id, s.seriesName " +
           "ORDER BY COALESCE(SUM(v.likeNumber), 0L) DESC")
    List<Object[]> findRankingByQuarterAndYear(@Param("months") List<Integer> months,
                                               @Param("year") int year);

    @Query(value = "SELECT DISTINCT SeriesID, SeriesName, Status FROM series ORDER BY SeriesID ASC", nativeQuery = true)
    List<Object[]> findAllSeriesDistinct();

    // Đếm theo đúng SessionID (không theo ngày) để tránh đếm nhầm phiếu của
    // phiên vote khác cho cùng series diễn ra cùng ngày (vd stop + defense).
    @Query("SELECT COUNT(sv) FROM SeriesVote sv WHERE sv.session.id = :sessionId")
    long countSeriesVoteBySession(@Param("sessionId") String sessionId);

    @Query("SELECT COUNT(sv) FROM SeriesVote sv " +
           "WHERE sv.session.id = :sessionId AND sv.board.id = :boardId")
    long countSeriesVoteByBoardAndSession(@Param("sessionId") String sessionId,
                                          @Param("boardId") String boardId);

    @Query("SELECT COUNT(sv) FROM SeriesVote sv " +
           "WHERE sv.session.id = :sessionId AND sv.vote = :voteChoice")
    long countSeriesVoteByChoiceAndSession(@Param("sessionId") String sessionId,
                                           @Param("voteChoice") String voteChoice);

    @Query(value = "SELECT Status FROM series WHERE SeriesID = :seriesId LIMIT 1", nativeQuery = true)
    String getSeriesStatus(@Param("seriesId") String seriesId);

    @Transactional
    @Modifying
    @Query("UPDATE Series s SET s.status = 'unfinish' WHERE s.status IN ('stopped', 'rewarded')")
    int resetSeriesStatus();
}
