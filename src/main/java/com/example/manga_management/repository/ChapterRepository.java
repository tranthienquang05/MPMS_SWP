package com.example.manga_management.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.manga_management.entity.Chapter;
import com.example.manga_management.entity.Series;

public interface ChapterRepository extends JpaRepository<Chapter, String> {

    List<Chapter> findBySeries(Series series);

    List<Chapter> findByStatus(String status);

    List<Chapter> findByStatusAndDeadlineBefore(String status, LocalDate deadline);

    List<Chapter> findBySeriesId(String seriesId);

    Optional<Chapter> findTopByOrderByIdDesc();

    Optional<Chapter> findTopBySeriesOrderByChapterNumberDesc(Series series);

    // Thêm 2 dòng này vào ChapterRepository (interface đã có sẵn), không sửa gì khác
    List<Chapter> findByStatusAndSeries_Proposal_Mangaka_Editor_User_Id(String status, String editorId);

    List<Chapter> findBySeries_IdAndStatus(String seriesId, String status);
}
