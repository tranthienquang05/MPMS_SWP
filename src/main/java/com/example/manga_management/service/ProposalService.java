package com.example.manga_management.service;

import com.example.manga_management.entity.Board;
import com.example.manga_management.entity.EditorialVote;
import com.example.manga_management.entity.Proposal;
import com.example.manga_management.repository.BoardRepository;
import com.example.manga_management.repository.EditorialVoteRepository;
import com.example.manga_management.repository.ProposalRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class ProposalService {

    private final ProposalRepository proposalRepository;
    private final BoardRepository boardRepository;
    private final EditorialVoteRepository voteRepository;

    public ProposalService(EditorialVoteRepository voteRepository,
            ProposalRepository proposalRepository,
            BoardRepository boardRepository) {
        this.voteRepository = voteRepository;
        this.proposalRepository = proposalRepository;
        this.boardRepository = boardRepository;
    }

    @Transactional
    public void submitVote(String proposalId, String action, String boardId) {
  
        if (voteRepository.existsByProposalIdAndBoardId(proposalId, boardId)) {
            throw new RuntimeException("Bạn đã bỏ phiếu cho dự án này rồi!");
        }


        Proposal proposal = proposalRepository.findById(proposalId).orElseThrow();
        Board board = boardRepository.findById(boardId).orElseThrow(); 


        EditorialVote vote = new EditorialVote();
        vote.setEvoteID(UUID.randomUUID().toString().substring(0, 6));
        vote.setProposal(proposal);
        vote.setBoard(board);
        vote.setVote(action);
        vote.setVoteDate(LocalDate.now());

        voteRepository.save(vote);

    // 4. Logic kiểm tra 60%
        checkAndFinalizeProposal(proposalId);
    }

    private void checkAndFinalizeProposal(String proposalId) {

    long totalBoardMembers = boardRepository.count(); 
    

    long currentVotes = voteRepository.countByProposalId(proposalId);


    if (currentVotes >= totalBoardMembers) {
        List<EditorialVote> votes = voteRepository.findByProposalId(proposalId);
        long passVotes = votes.stream().filter(v -> "pass".equals(v.getVote())).count();
        double passRate = (double) passVotes / votes.size();

        Proposal proposal = proposalRepository.findById(proposalId).orElse(null);
        if (proposal != null) {
            proposal.setStatus(passRate >= 0.6 ? "pass" : "reject");
            proposalRepository.save(proposal);
        }
    }
}
}