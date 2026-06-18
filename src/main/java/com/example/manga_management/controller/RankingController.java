package com.example.manga_management.controller;

import java.time.LocalDate;
import java.util.*;

import org.springframework.web.bind.annotation.*;

import com.example.manga_management.entity.*;
import com.example.manga_management.repository.*;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/manga/ranking")
public class RankingController {

    private final RankingRepository rankingRepository;
    private final EditorialVoteRepository editorialVoteRepository;
    private final ProposalRepository proposalRepository;
    private final BoardRepository boardRepository;

    public RankingController(RankingRepository rankingRepository,
            EditorialVoteRepository editorialVoteRepository,
            ProposalRepository proposalRepository,
            BoardRepository boardRepository) {
        this.rankingRepository = rankingRepository;
        this.editorialVoteRepository = editorialVoteRepository;
        this.proposalRepository = proposalRepository;
        this.boardRepository = boardRepository;
    }

    // ===== 1. API ranking =====
    @GetMapping
    public List<Map<String, Object>> getRanking(
            @RequestParam(defaultValue = "0") int month,
            @RequestParam(defaultValue = "2026") int year) {

        List<Object[]> rows = (month == 0)
                ? rankingRepository.findRankingByYear(year)
                : rankingRepository.findRankingByMonthAndYear(month, year);

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Object[] row = rows.get(i);
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("rank", i + 1);
            map.put("seriesId", row[0]);
            map.put("seriesName", row[1]);
            map.put("totalVotes", row[2]);
            result.add(map);
        }
        return result;
    }

    // ===== 2. API lấy 3 series cuối =====
    @GetMapping("/bottom")
    public List<Map<String, Object>> getBottom(
            @RequestParam(defaultValue = "0") int month,
            @RequestParam(defaultValue = "2026") int year,
            HttpSession session) {

        List<Object[]> rows = (month == 0)
                ? rankingRepository.findBottomByYear(year)
                : rankingRepository.findBottomByMonthAndYear(month, year);

        List<Object[]> bottom3 = rows.size() > 3 ? rows.subList(0, 3) : rows;

        // Lấy boardId từ session
        User user = (User) session.getAttribute("user");
        String boardId = (user != null)
                ? boardRepository.findByUserId(user.getId())
                        .map(Board::getId).orElse(null)
                : null;

        long totalBoardMembers = boardRepository.count();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : bottom3) {
            String seriesId = (String) row[0];

            long totalVoted = editorialVoteRepository.countBySeriesId(seriesId);
            long stopVote = editorialVoteRepository.countBySeriesIdAndVote(seriesId, "stop");
            boolean alreadyVoted = (boardId != null) &&
                    editorialVoteRepository.countBySeriesIdAndBoardId(seriesId, boardId) > 0;

            double percent = (totalVoted >= totalBoardMembers && totalBoardMembers > 0)
                    ? (stopVote * 100.0 / totalBoardMembers)
                    : 0;

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("seriesId", seriesId);
            map.put("seriesName", row[1]);
            map.put("totalVotes", row[2]);
            map.put("stopVote", stopVote);
            map.put("totalBoard", totalBoardMembers);
            map.put("stopPercent", Math.round(percent));
            map.put("alreadyVoted", alreadyVoted);
            result.add(map);
        }
        return result;
    }

    // ===== 3. API Board vote (stop hoặc keep) =====
    @PostMapping("/vote")
    public Map<String, Object> vote(
            @RequestParam String seriesId,
            @RequestParam String voteType,
            HttpSession session) {

        Map<String, Object> response = new LinkedHashMap<>();
        User user = (User) session.getAttribute("user");

        if (user == null) {
            response.put("success", false);
            response.put("message", "Bạn chưa đăng nhập!");
            return response;
        }

        Optional<Board> boardOpt = boardRepository.findByUserId(user.getId());
        if (boardOpt.isEmpty()) {
            response.put("success", false);
            response.put("message", "Bạn không phải thành viên Editorial Board!");
            return response;
        }
        Board board = boardOpt.get();

        if (editorialVoteRepository.countBySeriesIdAndBoardId(seriesId, board.getId()) > 0) {
            response.put("success", false);
            response.put("message", "Bạn đã vote series này rồi!");
            return response;
        }

        if (!voteType.equals("stop") && !voteType.equals("keep")) {
            response.put("success", false);
            response.put("message", "Loại vote không hợp lệ!");
            return response;
        }

        Optional<Proposal> proposalOpt = proposalRepository.findBySeriesId(seriesId);
        if (proposalOpt.isEmpty()) {
            response.put("success", false);
            response.put("message", "Không tìm thấy proposal của series này!");
            return response;
        }
        Proposal proposal = proposalOpt.get();

        // Lưu vote
        EditorialVote vote = new EditorialVote();
        vote.setEvoteID(generateEvoteId());
        vote.setProposal(proposal);
        vote.setBoard(board);
        vote.setVote(voteType);
        vote.setVoteDate(LocalDate.now());
        editorialVoteRepository.save(vote);

        // Đếm thống kê
        long totalBoardMembers = boardRepository.count();
        long totalVoted = editorialVoteRepository.countBySeriesId(seriesId);
        long stopVote = editorialVoteRepository.countBySeriesIdAndVote(seriesId, "stop");

        response.put("success", true);

        // Chưa đủ tất cả board vote → chỉ ghi nhận
        if (totalVoted < totalBoardMembers) {
            response.put("stopped", false);
            response.put("stopPercent", 0);
            response.put("message", "Đã ghi nhận vote! Chờ thêm "
                    + (totalBoardMembers - totalVoted) + " board nữa vote.");
            return response;
        }

        // Đã đủ tất cả board vote → tính %
        double percent = (stopVote * 100.0 / totalBoardMembers);
        response.put("stopPercent", Math.round(percent));

        if (percent >= 60 && proposal.getSeries() != null) {
            Series series = proposal.getSeries();
            series.setStatus("stopped");
            proposalRepository.save(proposal);
            response.put("stopped", true);
            response.put("message", "⚠️ Series đã bị DỪNG phát hành! ("
                    + Math.round(percent) + "% board vote dừng)");
        } else {
            response.put("stopped", false);
            response.put("message", "Tất cả board đã vote. Kết quả: "
                    + Math.round(percent) + "% vote dừng (chưa đủ 60% → series tiếp tục phát hành).");
        }

        return response;
    }

    // Sinh EvoteID 6 ký tự ngẫu nhiên
    private String generateEvoteId() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789";
        Random rand = new Random();
        StringBuilder sb = new StringBuilder("EV");
        for (int i = 0; i < 4; i++) {
            sb.append(chars.charAt(rand.nextInt(chars.length())));
        }
        return sb.toString();
    }

    // ===== 4. API xem lịch sử vote của 1 series =====
    @GetMapping("/vote-history")
    public Map<String, Object> getVoteHistory(@RequestParam String seriesId) {

        Map<String, Object> response = new LinkedHashMap<>();

        // Lấy proposal của series
        Optional<Proposal> proposalOpt = proposalRepository.findBySeriesId(seriesId);
        if (proposalOpt.isEmpty()) {
            response.put("success", false);
            response.put("message", "Không tìm thấy series!");
            return response;
        }

        // Lấy tất cả vote của proposal này
        List<EditorialVote> votes = editorialVoteRepository.findByProposalId(proposalOpt.get().getId());

        List<Map<String, Object>> voteList = new ArrayList<>();
        for (EditorialVote v : votes) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("evoteId", v.getEvoteID());
            map.put("boardId", v.getBoard().getId());
            map.put("boardName", v.getBoard().getUser().getFullname());
            map.put("vote", v.getVote());
            map.put("voteDate", v.getVoteDate().toString());
            voteList.add(map);
        }

        response.put("success", true);
        response.put("seriesId", seriesId);
        response.put("totalVotes", votes.size());
        response.put("history", voteList);
        return response;
    }
}