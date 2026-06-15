package com.example.manga_management.repository;

import com.example.manga_management.entity.VoteResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoteResultRepository extends JpaRepository<VoteResult, String> {
}
