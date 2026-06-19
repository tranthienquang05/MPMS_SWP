package com.example.manga_management.repository;

import com.example.manga_management.entity.Chapter;
import com.example.manga_management.entity.MangaPage;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MangaPageRepository extends JpaRepository<MangaPage, String> {
    List<MangaPage> findByChapter(Chapter chapter);
    Optional<MangaPage> findTopByOrderByIdDesc();
}
