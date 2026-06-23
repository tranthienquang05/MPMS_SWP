package com.example.manga_management.repository;

import com.example.manga_management.entity.Assistant;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AssistantRepository extends JpaRepository<Assistant, String> {
    List<Assistant> findByMangakaId(String mangakaId);
}
