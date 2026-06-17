package com.example.manga_management.repository;

import com.example.manga_management.entity.Board;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardRepository extends JpaRepository<Board, String> {
    Optional<Board> findByUserId(String userId);
}
