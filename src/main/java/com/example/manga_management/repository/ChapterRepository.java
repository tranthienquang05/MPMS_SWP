package com.example.manga_management.repository;

import com.example.manga_management.entity.Chapter;
import com.example.manga_management.entity.Series;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChapterRepository extends JpaRepository<Chapter, String> {
    List<Chapter> findBySeries(Series series);
}
