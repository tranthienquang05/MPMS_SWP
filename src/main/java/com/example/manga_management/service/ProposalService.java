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
    public void submitVote(String proposalId, String action, String userId) {
        // 1. Tìm Proposal
        Proposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new RuntimeException("Dự án không tồn tại!"));

        // 2. TÌM BOARD THEO USER ID (Thay vì giả định ID User là ID Board)
        // Giả sử bạn có phương thức này trong BoardRepository
        Board board = boardRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Tài khoản này không có quyền biên tập!"));

        // 3. Kiểm tra trùng lặp phiếu bầu
        if (voteRepository.existsByProposalIdAndBoardId(proposalId, board.getId())) {
            throw new RuntimeException("Bạn đã bỏ phiếu cho dự án này rồi!");
        }

        // 4. Lưu phiếu bầu
        EditorialVote vote = new EditorialVote();
        vote.setEvoteID(UUID.randomUUID().toString().substring(0, 6));
        vote.setProposal(proposal);
        vote.setBoard(board); // Dùng board tìm được
        vote.setVote(action);
        vote.setVoteDate(LocalDate.now());

        voteRepository.save(vote);

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