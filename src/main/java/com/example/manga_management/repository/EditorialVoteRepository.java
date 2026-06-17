package com.example.manga_management.repository;

import com.example.manga_management.entity.EditorialVote;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EditorialVoteRepository extends JpaRepository<EditorialVote, String> {
    List<EditorialVote> findByProposalId(String proposalId);

    boolean existsByProposalIdAndBoardId(String proposalId, String boardId);
}
