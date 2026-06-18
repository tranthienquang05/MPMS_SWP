package com.example.manga_management.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.manga_management.entity.Series;

@Repository
public interface SeriesRepository extends JpaRepository<Series, String> {
}
