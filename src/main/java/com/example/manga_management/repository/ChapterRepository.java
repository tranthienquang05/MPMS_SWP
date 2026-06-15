package com.example.manga_management.repository;

import com.example.manga_management.entity.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChapterRepository extends JpaRepository<Chapter, String> {
}
