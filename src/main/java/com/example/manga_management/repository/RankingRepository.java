package com.example.manga_management.repository;

import com.example.manga_management.entity.SeriesVote;
import com.example.manga_management.entity.VoteResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

public interface RankingRepository extends JpaRepository<VoteResult, String> {

    @Query("SELECT v.series.id, v.series.seriesName, SUM(v.voteNumber) " +
           "FROM VoteResult v WHERE v.year = :year " +
           "GROUP BY v.series.id, v.series.seriesName " +
           "ORDER BY SUM(v.voteNumber) DESC")
    List<Object[]> findRankingByYear(@Param("year") int year);

    @Query("SELECT v.series.id, v.series.seriesName, v.voteNumber " +
           "FROM VoteResult v WHERE v.month = :month AND v.year = :year " +
           "ORDER BY v.voteNumber DESC")
    List<Object[]> findRankingByMonthAndYear(@Param("month") int month,
                                              @Param("year") int year);

    @Query("SELECT v.series.id, v.series.seriesName, SUM(v.voteNumber) " +
           "FROM VoteResult v WHERE v.year = :year " +
           "GROUP BY v.series.id, v.series.seriesName " +
           "ORDER BY SUM(v.voteNumber) ASC")
    List<Object[]> findBottomByYear(@Param("year") int year);

    @Query("SELECT v.series.id, v.series.seriesName, v.voteNumber " +
           "FROM VoteResult v WHERE v.month = :month AND v.year = :year " +
           "ORDER BY v.voteNumber ASC")
    List<Object[]> findBottomByMonthAndYear(@Param("month") int month,
                                             @Param("year") int year);

    // ===== Queries for manual vote sessions (dùng SeriesVote cho cả 2 loại) =====

    @Query(value = "SELECT DISTINCT SeriesID, SeriesName, Status FROM series ORDER BY SeriesID ASC", nativeQuery = true)
    List<Object[]> findAllSeriesDistinct();

    @Query("SELECT COUNT(sv) FROM SeriesVote sv " +
           "WHERE sv.series.id = :seriesId AND sv.voteDate >= :since")
    long countSeriesVoteSince(@Param("seriesId") String seriesId,
                              @Param("since") LocalDate since);

    @Query("SELECT COUNT(sv) FROM SeriesVote sv " +
           "WHERE sv.series.id = :seriesId AND sv.board.id = :boardId AND sv.voteDate >= :since")
    long countSeriesVoteByBoardSince(@Param("seriesId") String seriesId,
                                     @Param("boardId") String boardId,
                                     @Param("since") LocalDate since);

    @Query("SELECT COUNT(sv) FROM SeriesVote sv " +
           "WHERE sv.series.id = :seriesId AND sv.vote = :voteChoice AND sv.voteDate >= :since")
    long countSeriesVoteByChoiceSince(@Param("seriesId") String seriesId,
                                      @Param("voteChoice") String voteChoice,
                                      @Param("since") LocalDate since);

    @Query("SELECT sv FROM SeriesVote sv " +
           "WHERE sv.series.id = :seriesId AND sv.voteDate >= :since")
    List<SeriesVote> findSeriesVotesSince(@Param("seriesId") String seriesId,
                                          @Param("since") LocalDate since);

    @Query(value = "SELECT Status FROM series WHERE SeriesID = :seriesId LIMIT 1", nativeQuery = true)
    String getSeriesStatus(@Param("seriesId") String seriesId);

    @Transactional
    @Modifying
    @Query("UPDATE Series s SET s.status = 'unfinish' WHERE s.status IN ('stopped', 'rewarded')")
    int resetSeriesStatus();
}
