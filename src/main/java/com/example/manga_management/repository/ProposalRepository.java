package com.example.manga_management.repository;

import com.example.manga_management.entity.Proposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProposalRepository extends JpaRepository<Proposal, String> {
    List<Proposal> findByStatus(String status);

    List<Proposal> findByStatusAndMangaka_Editor_Id(String status, String editorId);

    @Query("SELECT p FROM Proposal p WHERE p.series.id = :seriesId")
    Optional<Proposal> findBySeriesId(@Param("seriesId") String seriesId);

    List<Proposal> findByMangaka_Id(String mangakaId);

    List<Proposal> findByStatusAndMangaka_Id(String status, String mangakaId);

    List<Proposal> findByStatusInAndMangaka_Id(List<String> statuses, String mangakaId);
}
