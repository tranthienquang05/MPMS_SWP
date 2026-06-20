package com.example.manga_management.repository;

import com.example.manga_management.entity.VoteSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VoteSessionRepository extends JpaRepository<VoteSession, String> {
    List<VoteSession> findByStatusOrderByCreatedAtDesc(String status);
    boolean existsBySeriesIdAndStatus(String seriesId, String status);
    Optional<VoteSession> findFirstBySeriesIdAndVoteTypeAndStatus(String seriesId, String voteType, String status);
}
