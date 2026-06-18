package com.example.manga_management.repository;

import com.example.manga_management.entity.VoteResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
