package com.example.manga_management.repository;

import java.util.Optional;

import com.example.manga_management.entity.PublicDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicDateRepository extends JpaRepository<PublicDate, String> {
    Optional<PublicDate> findTopByOrderByIdDesc();
}
