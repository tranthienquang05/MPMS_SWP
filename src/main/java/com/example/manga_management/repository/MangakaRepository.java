package com.example.manga_management.repository;

import com.example.manga_management.entity.Mangaka;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MangakaRepository extends JpaRepository<Mangaka, String> {
}
