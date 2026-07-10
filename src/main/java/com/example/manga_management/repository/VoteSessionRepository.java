package com.example.manga_management.repository;

import com.example.manga_management.entity.VoteSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VoteSessionRepository extends JpaRepository<VoteSession, String> {
    List<VoteSession> findByStatusOrderByCreatedAtDesc(String status);
    List<VoteSession> findByAutoCreatedOrderByCreatedAtDesc(boolean autoCreated);
    List<VoteSession> findByCreatedBy_User_IdOrderByCreatedAtDesc(String userId);
    boolean existsBySeriesIdAndStatus(String seriesId, String status);
    boolean existsBySeriesIdAndAutoCreatedAndStatus(String seriesId, boolean autoCreated, String status);
    boolean existsBySeriesIdAndVoteType(String seriesId, String voteType);
    Optional<VoteSession> findFirstBySeriesIdAndVoteTypeAndStatus(String seriesId, String voteType, String status);

    List<VoteSession> findBySeriesIdOrderByCreatedAtDesc(String seriesId);
}
