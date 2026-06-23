package com.example.manga_management.repository;

import com.example.manga_management.entity.Submission;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionRepository extends JpaRepository<Submission, String> {
    Submission findByPageId(String pageId);

    Optional<Submission> findTopByOrderByIdDesc();
    boolean existsByPageIdIdAndStatus(
        String pageId,
        String status);

    Optional<Submission> findByPageIdId(String pageId);
}
