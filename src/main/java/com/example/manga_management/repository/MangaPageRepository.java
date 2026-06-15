package com.example.manga_management.repository;

import com.example.manga_management.entity.MangaPage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MangaPageRepository extends JpaRepository<MangaPage, String> {
}
