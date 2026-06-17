package com.example.manga_management.service;

import com.example.manga_management.entity.EditorialVote;
import com.example.manga_management.entity.Proposal;
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

    private final EditorialVoteRepository voteRepository;

    ProposalService(EditorialVoteRepository voteRepository, ProposalRepository proposalRepository) {
        this.voteRepository = voteRepository;
        this.proposalRepository = proposalRepository;
    }

    @Transactional
    public void submitVote(String proposalId, String action) {
    
        Proposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy dự án với ID: " + proposalId));

        EditorialVote vote = new EditorialVote();
        
        vote.setId(UUID.randomUUID().toString().substring(0, 6)); 
        
        vote.setProposal(proposal); 
        vote.setVote(action);       
        vote.setVoteDate(LocalDate.now());
        
        voteRepository.save(vote);

        checkAndFinalizeProposal(proposalId);
    }

    private void checkAndFinalizeProposal(String proposalId) {
        List<EditorialVote> votes = voteRepository.findByProposalId(proposalId);
        int totalBoardMembers = 3; 

        // Chỉ xử lý khi đã đủ số phiếu
        if (votes.size() >= totalBoardMembers) {
            long passVotes = votes.stream().filter(v -> "pass".equals(v.getVote())).count();
            double passRate = (double) passVotes / votes.size();

            Proposal proposal = proposalRepository.findById(proposalId).orElse(null);
            if (proposal != null) {
                if (passRate >= 0.6) {
                    proposal.setStatus("pass");
                } else {
                    proposal.setStatus("reject");
                }
                proposalRepository.save(proposal);
            }
        }
    }
}