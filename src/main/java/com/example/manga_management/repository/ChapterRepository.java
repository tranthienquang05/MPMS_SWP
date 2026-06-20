package com.example.manga_management.repository;

import com.example.manga_management.entity.Chapter;
import com.example.manga_management.entity.Series;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChapterRepository extends JpaRepository<Chapter, String> {
    List<Chapter> findBySeries(Series series);

    List<Chapter> findBySeriesId(String seriesId);

    Optional<Chapter> findTopByOrderByIdDesc();
    Optional<Chapter> findTopBySeriesOrderByChapterNumberDesc(Series series);
}
