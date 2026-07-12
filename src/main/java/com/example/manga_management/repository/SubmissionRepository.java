package com.example.manga_management.repository;

import com.example.manga_management.entity.Submission;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionRepository extends JpaRepository<Submission, String> {
    Optional<Submission> findTopByOrderByIdDesc();

    boolean existsByPageIdIdAndStatus(
            String pageId,
            String status);

    // 1 trang có thể có nhiều submission theo thời gian (nhiều vòng giao việc
    // khác nhau, cho nhiều assistant khác nhau) — luôn lấy bản mới nhất làm task
    // "hiện hành" của trang.
    Optional<Submission> findTopByPageIdIdOrderByCreatedAtDesc(String pageId);

    List<Submission> findByAssistant_IdOrderByDeadlineAsc(String assistantId);

    List<Submission> findByStatusAndDeadlineBetween(String status, LocalDateTime start, LocalDateTime end);

    List<Submission> findByStatusAndDeadlineBefore(String status, LocalDateTime deadline);

    List<Submission> findByAssistant_IdAndStatus(
            String assistantId,
            String status);

    List<Submission> findByAssistant_Id(String id);

    List<Submission> findByAssistant_Mangaka_IdOrderByCreatedAtDesc(String mangakaId);

    List<Submission> findByAssistant_Mangaka_IdAndStatus(String mangakaId, String status);

    List<Submission> findByPageIdId(String pageId); 
}
