package com.example.manga_management.repository;

import com.example.manga_management.entity.Proposal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProposalRepository extends JpaRepository<Proposal, String> {
}
