package com.example.manga_management.repository;

import com.example.manga_management.entity.PublicDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicDateRepository extends JpaRepository<PublicDate, String> {
}
