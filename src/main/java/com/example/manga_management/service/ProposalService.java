package com.example.manga_management.service;

import com.example.manga_management.entity.Board;
import com.example.manga_management.entity.BoardProposalComment;
import com.example.manga_management.entity.Proposal;
import com.example.manga_management.repository.BoardProposalCommentRepository;
import com.example.manga_management.repository.BoardRepository;
import com.example.manga_management.repository.ProposalRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProposalService {

    private final ProposalRepository proposalRepository;
    private final BoardProposalCommentRepository boardCommentRepository;
    private final BoardRepository boardRepository;

    public ProposalService(ProposalRepository proposalRepository,
            BoardProposalCommentRepository boardCommentRepository,
            BoardRepository boardRepository) {
        this.proposalRepository = proposalRepository;
        this.boardCommentRepository = boardCommentRepository;
        this.boardRepository = boardRepository;
    }

    public String generateNextProposalId() {
        long count = proposalRepository.count();
        return String.format("PPS%03d", count + 1);
    }

    /**
     * Board member vote + comment for a proposal in board_check status.
     */
    public Proposal submitBoardVote(String proposalId, String boardId, String action, String content) {
        Proposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new RuntimeException("Khong tim thay de xuat: " + proposalId));

        if (!"board_check".equals(proposal.getStatus())) {
            resultCheck(proposal);
        }

        if (boardCommentRepository.existsByProposal_IdAndBoard_Id(proposalId, boardId)) {
            throw new RuntimeException("Ban da vote cho du an nay roi!");
        }

        if (!"pass".equals(action) && !"reject".equals(action)) {
            throw new RuntimeException("Hanh dong vote khong hop le!");
        }

        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new RuntimeException("Khong tim thay thanh vien hoi dong: " + boardId));

        BoardProposalComment vote = BoardProposalComment.builder()
                .proposal(proposal)
                .board(board)
                .action(action)
                .content(content)
                .build();
        boardCommentRepository.save(vote);

        evaluateProposalResult(proposal);
        return proposal;
    }

    private void resultCheck(Proposal proposal) {
        throw new RuntimeException("De xuat nay khong o trang thai cho hoi dong bo phieu!");
    }

    public void evaluateProposalResult(Proposal proposal) {
        long passVotes = boardCommentRepository.countByProposal_IdAndAction(proposal.getId(), "pass");
        long rejectVotes = boardCommentRepository.countByProposal_IdAndAction(proposal.getId(), "reject");
        long totalBoards = boardRepository.count();

        if (totalBoards <= 0) {
            return;
        }

        if (passVotes + rejectVotes < totalBoards) {
            return;
        }

        proposal.setStatus(passVotes > rejectVotes ? "passed" : "locked");
        proposalRepository.save(proposal);
    }

    public List<BoardProposalComment> getCommentsForProposal(String proposalId) {
        return boardCommentRepository.findByProposal_Id(proposalId);
    }
}
