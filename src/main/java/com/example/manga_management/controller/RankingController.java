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

    // ===== 2. Lấy danh sách series cho dropdown tạo vote =====
    @GetMapping("/all-series")
    public List<Map<String, Object>> getAllSeries() {
        List<Series> list = rankingRepository.findAllSeriesOrdered();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Series s : list) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", s.getId());
            map.put("name", s.getSeriesName());
            map.put("status", s.getStatus());
            result.add(map);
        }
        return result;
    }

    // ===== 3. Tạo phiên vote thủ công =====
    @PostMapping("/create-session")
    public Map<String, Object> createSession(
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

        if (!voteType.equals("stop") && !voteType.equals("reward")) {
            response.put("success", false);
            response.put("message", "Loại vote không hợp lệ (stop hoặc reward)!");
            return response;
        }

        Optional<Series> seriesOpt = seriesRepository.findById(seriesId);
        if (seriesOpt.isEmpty()) {
            response.put("success", false);
            response.put("message", "Không tìm thấy series!");
            return response;
        }

        if (voteSessionRepository.existsBySeriesIdAndStatus(seriesId, "active")) {
            response.put("success", false);
            response.put("message", "Series này đang có phiên vote đang mở rồi! Phải đóng phiên hiện tại trước.");
            return response;
        }

        VoteSession vs = new VoteSession();
        vs.setId(generateSessionId());
        vs.setSeries(seriesOpt.get());
        vs.setCreatedBy(boardOpt.get());
        vs.setVoteType(voteType);
        vs.setStatus("active");
        vs.setCreatedAt(LocalDate.now());
        voteSessionRepository.save(vs);

        response.put("success", true);
        response.put("sessionId", vs.getId());
        response.put("message", "Đã tạo phiên vote " + (voteType.equals("stop") ? "dừng" : "khen thưởng")
                + " cho series \"" + seriesOpt.get().getSeriesName() + "\"!");
        return response;
    }

    // ===== 4. Lấy danh sách phiên vote đang mở (tất cả board thấy) =====
    @GetMapping("/active-sessions")
    public List<Map<String, Object>> getActiveSessions(HttpSession session) {
        User user = (User) session.getAttribute("user");
        String boardId = null;
        if (user != null) {
            boardId = boardRepository.findByUserId(user.getId())
                    .map(Board::getId).orElse(null);
        }

        long totalBoards = boardRepository.count();
        List<VoteSession> sessions = voteSessionRepository.findByStatusOrderByCreatedAtDesc("active");
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
            map.put("createdAt", vs.getCreatedAt().toString());
            map.put("createdBy", vs.getCreatedBy().getUser().getFullname());
            map.put("voted", voted);
            map.put("totalBoards", totalBoards);
            map.put("positiveVotes", positive);
            map.put("percent", Math.round(percent));
            map.put("alreadyVoted", alreadyVoted);
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

        SeriesVote sv = new SeriesVote();
        sv.setID(generateSvoteId());
        sv.setSeries(vs.getSeries());
        sv.setBoard(board);
        sv.setVote(voteChoice);
        sv.setVoteDate(LocalDate.now());
        seriesVoteRepository.save(sv);

        // Kiểm tra kết quả sau khi vote
        long totalBoards = boardRepository.count();
        String positiveChoice = vs.getVoteType().equals("stop") ? "stop" : "reward";
        long voted = rankingRepository.countSeriesVoteSince(seriesId, since);
        long positive = rankingRepository.countSeriesVoteByChoiceSince(seriesId, positiveChoice, since);

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

    // ===== 6. Lịch sử vote của một phiên =====
    @GetMapping("/session-history")
    public Map<String, Object> getSessionHistory(@RequestParam String sessionId) {
        Map<String, Object> response = new LinkedHashMap<>();

        Optional<VoteSession> sessionOpt = voteSessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            response.put("success", false);
            response.put("message", "Không tìm thấy phiên vote!");
            return response;
        }
        VoteSession vs = sessionOpt.get();
        String seriesId = vs.getSeries().getId();
        LocalDate since = vs.getCreatedAt();

        List<Map<String, Object>> voteList = new ArrayList<>();

        List<SeriesVote> votes = rankingRepository.findSeriesVotesSince(seriesId, since);
        for (SeriesVote v : votes) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("boardId", v.getBoard().getId());
            m.put("boardName", v.getBoard().getUser().getFullname());
            m.put("choice", v.getVote());
            m.put("voteDate", v.getVoteDate().toString());
            voteList.add(m);
        }

        response.put("success", true);
        response.put("sessionId", sessionId);
        response.put("seriesId", seriesId);
        response.put("seriesName", vs.getSeries().getSeriesName());
        response.put("voteType", vs.getVoteType());
        response.put("status", vs.getStatus());
        response.put("totalVotes", voteList.size());
        response.put("history", voteList);
        return response;
    }

    // ===== 7. Reset toàn bộ vote để demo =====
    @GetMapping("/reset-vote")
    public Map<String, Object> resetVote() {
        voteSessionRepository.deleteAll();
        seriesVoteRepository.deleteAll();

        // Reset trạng thái series stopped/rewarded → unfinish
        List<Series> allSeries = seriesRepository.findAll();
        for (Series s : allSeries) {
            if ("stopped".equals(s.getStatus()) || "rewarded".equals(s.getStatus())) {
                s.setStatus("unfinish");
                seriesRepository.save(s);
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Đã reset toàn bộ vote và trạng thái series!");
        return response;
    }

    private String generateSessionId() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789";
        Random rand = new Random();
        StringBuilder sb = new StringBuilder("VS");
        for (int i = 0; i < 4; i++) sb.append(chars.charAt(rand.nextInt(chars.length())));
        return sb.toString();
    }

    private String generateSvoteId() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789";
        Random rand = new Random();
        StringBuilder sb = new StringBuilder("SV");
        for (int i = 0; i < 4; i++) sb.append(chars.charAt(rand.nextInt(chars.length())));
        return sb.toString();
    }
}