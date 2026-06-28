package com.example.manga_management.controller;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;

import com.example.manga_management.entity.*;
import com.example.manga_management.repository.*;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/manga/ranking")
public class RankingController {

    private final RankingRepository rankingRepository;
    private final BoardRepository boardRepository;
    private final VoteSessionRepository voteSessionRepository;
    private final SeriesVoteRepository seriesVoteRepository;
    private final SeriesRepository seriesRepository;

    public RankingController(RankingRepository rankingRepository,
            BoardRepository boardRepository,
            VoteSessionRepository voteSessionRepository,
            SeriesVoteRepository seriesVoteRepository,
            SeriesRepository seriesRepository) {
        this.rankingRepository = rankingRepository;
        this.boardRepository = boardRepository;
        this.voteSessionRepository = voteSessionRepository;
        this.seriesVoteRepository = seriesVoteRepository;
        this.seriesRepository = seriesRepository;
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

    // ===== 2. Lấy danh sách phiên vote đang mở (tất cả board thấy) =====
    @GetMapping("/active-sessions")
    public List<Map<String, Object>> getActiveSessions(HttpSession session) {
        User user = (User) session.getAttribute("user");
        String boardId = null;
        if (user != null) {
            boardId = boardRepository.findByUserId(user.getId())
                    .map(Board::getId).orElse(null);
        }

        long totalBoards = boardRepository.count();
        // Lấy tất cả phiên (do Admin tạo) - cả active và closed
        List<VoteSession> sessions = voteSessionRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Map<String, Object>> result = new ArrayList<>();

        for (VoteSession vs : sessions) {
            String sid = vs.getSeries().getId();
            LocalDate since = vs.getCreatedAt();
            String positiveChoice = vs.getVoteType().equals("stop") ? "stop" : "reward";

            long voted = rankingRepository.countSeriesVoteSince(sid, since);
            long positive = rankingRepository.countSeriesVoteByChoiceSince(sid, positiveChoice, since);
            boolean alreadyVoted = boardId != null &&
                    rankingRepository.countSeriesVoteByBoardSince(sid, boardId, since) > 0;

            double percent = (totalBoards > 0 && voted >= totalBoards)
                    ? (positive * 100.0 / totalBoards) : 0;

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("sessionId", vs.getId());
            map.put("seriesId", sid);
            map.put("seriesName", vs.getSeries().getSeriesName());
            map.put("voteType", vs.getVoteType());
            map.put("sessionStatus", vs.getStatus());
            map.put("createdAt", vs.getCreatedAt().toString());
            map.put("autoCreated", vs.isAutoCreated());
            map.put("createdBy", vs.getCreatedBy() != null
                    ? vs.getCreatedBy().getUser().getFullname()
                    : "Quản trị viên");
            map.put("voted", voted);
            map.put("totalBoards", totalBoards);
            map.put("positiveVotes", positive);
            map.put("percent", Math.round(percent));
            map.put("alreadyVoted", alreadyVoted);
            boolean isCreator = !vs.isAutoCreated() && boardId != null
                    && vs.getCreatedBy() != null
                    && vs.getCreatedBy().getId().equals(boardId);
            map.put("isCreator", isCreator);
            result.add(map);
        }
        return result;
    }

    // ===== 5. Board cast vote vào phiên =====
    @PostMapping("/cast-session-vote")
    public Map<String, Object> castSessionVote(
            @RequestParam String sessionId,
            @RequestParam String voteChoice,
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

        Optional<VoteSession> sessionOpt = voteSessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            response.put("success", false);
            response.put("message", "Không tìm thấy phiên vote!");
            return response;
        }
        VoteSession vs = sessionOpt.get();

        if (!vs.getStatus().equals("active")) {
            response.put("success", false);
            response.put("message", "Phiên vote này đã đóng!");
            return response;
        }

        String seriesId = vs.getSeries().getId();
        LocalDate since = vs.getCreatedAt();

        // Validate lựa chọn theo loại vote
        if (vs.getVoteType().equals("stop")) {
            if (!voteChoice.equals("stop") && !voteChoice.equals("keep")) {
                response.put("success", false);
                response.put("message", "Lựa chọn không hợp lệ (stop hoặc keep)!");
                return response;
            }
        } else {
            if (!voteChoice.equals("reward") && !voteChoice.equals("against")) {
                response.put("success", false);
                response.put("message", "Lựa chọn không hợp lệ (reward hoặc against)!");
                return response;
            }
        }

        if (rankingRepository.countSeriesVoteByBoardSince(seriesId, board.getId(), since) > 0) {
            response.put("success", false);
            response.put("message", "Bạn đã vote trong phiên này rồi!");
            return response;
        }

        // Đếm trước khi save để tránh lỗi JPA flush timing
        long totalBoards = boardRepository.count();
        String positiveChoice = vs.getVoteType().equals("stop") ? "stop" : "reward";
        long votedBefore = rankingRepository.countSeriesVoteSince(seriesId, since);
        long positiveBefore = rankingRepository.countSeriesVoteByChoiceSince(seriesId, positiveChoice, since);

        SeriesVote sv = new SeriesVote();
        sv.setID(generateSvoteId());
        sv.setSeries(vs.getSeries());
        sv.setBoard(board);
        sv.setVote(voteChoice);
        sv.setVoteDate(LocalDate.now());
        seriesVoteRepository.save(sv);

        long voted = votedBefore + 1;
        long positive = positiveBefore + (voteChoice.equals(positiveChoice) ? 1 : 0);

        response.put("success", true);

        if (voted < totalBoards) {
            response.put("message", "Đã ghi nhận vote! Còn " + (totalBoards - voted) + " board chưa vote.");
            return response;
        }

        // Đủ tất cả board → tính kết quả
        double percent = totalBoards > 0 ? (positive * 100.0 / totalBoards) : 0;
        response.put("percent", Math.round(percent));

        vs.setStatus("closed");
        voteSessionRepository.save(vs);

        Series series = vs.getSeries();
        if (vs.getVoteType().equals("stop")) {
            if (percent >= 60) {
                series.setStatus("stopped");
                seriesRepository.save(series);
                response.put("message", "⚠️ Tất cả board đã vote. Series bị DỪNG! (" + Math.round(percent) + "% đồng ý dừng)");
            } else {
                response.put("message", "Tất cả board đã vote. Kết quả: " + Math.round(percent) + "% vote dừng → Series tiếp tục.");
            }
        } else {
            if (percent >= 60) {
                series.setStatus("rewarded");
                seriesRepository.save(series);
                response.put("message", "🏆 Tất cả board đã vote. Series được KHEN THƯỞNG! (" + Math.round(percent) + "% đồng ý)");
            } else {
                response.put("message", "Tất cả board đã vote. Kết quả: " + Math.round(percent) + "% vote khen thưởng → Không đủ 60%.");
            }
        }
        return response;
    }

    // ===== 6. Reset toàn bộ vote để demo(http://localhost:8080/manga/ranking/reset-vote) =====
    @GetMapping("/reset-vote")
    public void resetVote(HttpServletResponse httpResp) throws IOException {
        // Xóa SeriesVote trước, sau đó VoteSession để tránh lỗi FK
        seriesVoteRepository.deleteAll();
        voteSessionRepository.deleteAll();

        // Dùng UPDATE trực tiếp để tránh lỗi duplicate ID khi load entity
        rankingRepository.resetSeriesStatus();

        httpResp.sendRedirect("/manga/editor");
    }

    private String generateSvoteId() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789";
        Random rand = new Random();
        StringBuilder sb = new StringBuilder("SV");
        for (int i = 0; i < 4; i++) sb.append(chars.charAt(rand.nextInt(chars.length())));
        return sb.toString();
    }
}