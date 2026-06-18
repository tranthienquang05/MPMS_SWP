package com.example.manga_management.repository;

<<<<<<< HEAD
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.manga_management.entity.Series;

@Repository
=======
import com.example.manga_management.entity.Series;
import org.springframework.data.jpa.repository.JpaRepository;

>>>>>>> e02d47f27c32981545a20f10452509c55e0c00a8
public interface SeriesRepository extends JpaRepository<Series, String> {
}
