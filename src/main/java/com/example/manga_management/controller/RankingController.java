package com.example.manga_management.controller;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

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

    // ===== 2. Lấy danh sách series cho dropdown tạo vote =====
    @GetMapping("/all-series")
    public List<Map<String, Object>> getAllSeries() {
        List<Object[]> rows = rankingRepository.findAllSeriesDistinct();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", row[0]);
            map.put("name", row[1]);
            map.put("status", row[2]);
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

        String seriesStatus = seriesOpt.get().getStatus();
        if ("stopped".equals(seriesStatus) || "rewarded".equals(seriesStatus)) {
            response.put("success", false);
            response.put("message", "Series này đã có kết quả vote, không thể tạo phiên mới!");
            return response;
        }

        if (voteSessionRepository.existsBySeriesIdAndStatus(seriesId, "active")) {
            response.put("success", false);
            response.put("message", "Series này đang có phiên vote đang mở rồi! Phải đóng phiên hiện tại trước.");
            return response;
        }

        if (voteSessionRepository.existsBySeriesIdAndVoteType(seriesId, voteType)) {
            response.put("success", false);
            response.put("message", "Series này đã từng có phiên vote "
                    + (voteType.equals("stop") ? "dừng" : "khen thưởng")
                    + " rồi, không thể tạo lại!");
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
        // Lấy tất cả phiên do board tạo thủ công (cả active lẫn closed)
        List<VoteSession> sessions = voteSessionRepository.findByAutoCreatedOrderByCreatedAtDesc(false);
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
            map.put("createdBy", vs.getCreatedBy().getUser().getFullname());
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

    // ===== 7. Board tạo phiên vote dừng cho 3 series cuối =====
    @PostMapping("/create-bottom-sessions")
    public Map<String, Object> createBottomSessions(
            @RequestParam(defaultValue = "0") int month,
            @RequestParam(defaultValue = "2026") int year,
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

        try {
            List<Object[]> rows = (month == 0)
                    ? rankingRepository.findBottomByYear(year)
                    : rankingRepository.findBottomByMonthAndYear(month, year);

            int count = Math.min(3, rows.size());
            List<String> createdNames = new ArrayList<>();
            List<String> skippedNames = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                String sid = (String) rows.get(i)[0];
                String sname = (String) rows.get(i)[1];

                Optional<Series> seriesOpt = seriesRepository.findById(sid);
                if (seriesOpt.isEmpty()) continue;
                String seriesStatus = seriesOpt.get().getStatus();

                // Skip nếu series đã có kết quả (stopped/rewarded) hoặc đã từng có phiên vote dừng
                if ("stopped".equals(seriesStatus) || "rewarded".equals(seriesStatus)
                        || voteSessionRepository.existsBySeriesIdAndVoteType(sid, "stop")) {
                    skippedNames.add(sname);
                    continue;
                }

                VoteSession vs = new VoteSession();
                vs.setId(generateSessionId());
                vs.setSeries(seriesOpt.get());
                vs.setCreatedBy(board);
                vs.setVoteType("stop");
                vs.setStatus("active");
                vs.setCreatedAt(LocalDate.now());
                vs.setAutoCreated(false);
                voteSessionRepository.save(vs);
                createdNames.add(sname);
            }

            String message;
            if (skippedNames.isEmpty()) {
                message = "Đã tạo phiên vote cho 3 series cuối bảng: "
                        + String.join(", ", createdNames) + "!";
            } else if (createdNames.isEmpty()) {
                message = "Cả 3 series cuối bảng (" + String.join(", ", skippedNames)
                        + ") đều đã có phiên vote trước đó, không tạo được phiên mới!";
            } else {
                message = "Series " + String.join(", ", skippedNames)
                        + " đã có phiên vote trước đó. Chỉ tạo được phiên vote cho: "
                        + String.join(", ", createdNames);
            }

            response.put("success", true);
            response.put("message", message);
        } catch (Exception e) {
            response.put("success", false);
            response.put("created", 0);
            response.put("message", "Lỗi tạo phiên: " + e.getMessage());
        }
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