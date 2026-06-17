package com.example.manga_management.repository;

import com.example.manga_management.entity.EditorialVote;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EditorialVoteRepository extends JpaRepository<EditorialVote, String> {
    boolean existsByProposalIdAndBoardId(String proposalId, String boardId);

    List<EditorialVote> findByProposalId(String proposalId);

    long countByProposalId(String proposalId);
}
