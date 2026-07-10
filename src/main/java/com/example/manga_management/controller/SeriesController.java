package com.example.manga_management.controller;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.manga_management.entity.Chapter;
import com.example.manga_management.entity.LikeResult;
import com.example.manga_management.entity.Proposal;
import com.example.manga_management.entity.Series;
import com.example.manga_management.entity.SeriesVote;
import com.example.manga_management.entity.VoteSession;
import com.example.manga_management.repository.ChapterRepository;
import com.example.manga_management.repository.LikeResultRepository;
import com.example.manga_management.repository.SeriesRepository;
import com.example.manga_management.repository.SeriesVoteRepository;
import com.example.manga_management.repository.VoteSessionRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/series")
@Tag(name = "Series", description = "Series Management APIs")
public class SeriesController {

    @Autowired
    private SeriesRepository seriesRepository;
    @Autowired
    private ChapterRepository chapterRepository;
    @Autowired
    private SeriesVoteRepository seriesVoteRepository;
    @Autowired
    private LikeResultRepository likeResultRepository;
    @Autowired
    private VoteSessionRepository voteSessionRepository;

    @GetMapping
    @Operation(summary = "[SWAGGER] Lấy danh sách tất cả series")
    @ResponseBody
    public Map<String, Object> getAllSeries() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Series> series = seriesRepository.findAll();
            result.put("status", "success");
            result.put("series", series);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }

    @GetMapping("/mangaka/{mangakaId}")
    @Operation(summary = "[SWAGGER] Lấy danh sách series theo Mangaka ID")
    @ResponseBody
    public Map<String, Object> getSeriesByMangakaId(@PathVariable String mangakaId) {
        Map<String, Object> result = new HashMap<>();

        List<Series> seriesList = seriesRepository.findByProposal_Mangaka_Id(mangakaId);
        if (seriesList.isEmpty()) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy series nào của Mangaka: " + mangakaId);
            return result;
        }

        result.put("status", "success");
        result.put("mangakaId", mangakaId);
        result.put("series", seriesList);
        return result;
    }

    @GetMapping("/{seriesId}")
    @Operation(summary = "[SWAGGER] Lấy thông tin chi tiết một series")
    @ResponseBody
    public Map<String, Object> getSeriesById(@PathVariable String seriesId) {
        Map<String, Object> result = new HashMap<>();

        Series series = seriesRepository.findById(seriesId).orElse(null);
        if (series == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy series: " + seriesId);
            return result;
        }

        result.put("status", "success");
        result.put("series", series);
        return result;
    }

    @GetMapping("/{seriesId}/info")
    @Operation(summary = "Xem toàn bộ thông tin series: proposal, thể loại, vote, view, số chapter")
    @ResponseBody
    public Map<String, Object> getSeriesInfo(@PathVariable String seriesId) {
        Map<String, Object> result = new HashMap<>();

        Series series = seriesRepository.findById(seriesId).orElse(null);
        if (series == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy series: " + seriesId);
            return result;
        }

        Proposal proposal = series.getProposal();
        Map<String, Object> proposalInfo = new HashMap<>();
        if (proposal != null) {
            proposalInfo.put("id", proposal.getId());
            proposalInfo.put("genre", proposal.getGenre());
            proposalInfo.put("editorScore", proposal.getEditorScore());
            proposalInfo.put("comment", proposal.getComment());
            proposalInfo.put("createdAt", proposal.getCreatedAt());
            proposalInfo.put("mangakaName",
                    proposal.getMangaka() != null && proposal.getMangaka().getUser() != null
                            ? proposal.getMangaka().getUser().getFullname()
                            : null);
        }

        List<Chapter> chapters = chapterRepository.findBySeriesId(seriesId);
        int totalViews = chapters.stream().mapToInt(Chapter::getViewCount).sum();
        Map<String, Long> chapterStats = new HashMap<>();
        chapterStats.put("total", (long) chapters.size());
        chapterStats.put("unfinish", chapters.stream().filter(c -> "unfinish".equals(c.getStatus())).count());
        chapterStats.put("waitingReview", chapters.stream().filter(c -> "finish".equals(c.getStatus())).count());
        chapterStats.put("pass", chapters.stream().filter(c -> "pass".equals(c.getStatus())).count());
        chapterStats.put("published", chapters.stream().filter(c -> "published".equals(c.getStatus())).count());

        long totalVotes = seriesVoteRepository.countBySeries_Id(seriesId);
        List<LikeResult> likeResults = likeResultRepository.findBySeries_Id(seriesId);
        int totalLikes = likeResults.stream().mapToInt(LikeResult::getLikeNumber).sum();
        int totalDislikes = likeResults.stream().mapToInt(LikeResult::getDislikeNumber).sum();

        // Lịch sử các phiên vote (stop/reward/defense) — hiện lý do + hồ sơ bảo vệ
        List<Map<String, Object>> voteSessions = voteSessionRepository
                .findBySeriesIdOrderByCreatedAtDesc(seriesId)
                .stream()
                .map(vs -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("sessionId", vs.getId());
                    map.put("voteType", vs.getVoteType());
                    map.put("status", vs.getStatus());
                    map.put("createdAt", vs.getCreatedAt());
                    map.put("reason", vs.getReason());
                    map.put("defenseFilePath", vs.getDefenseFilePath());
                    map.put("defenseNote", vs.getDefenseNote());
                    return map;
                })
                .toList();

        // Nhận xét của từng thành viên hội đồng khi vote (giống comment vote proposal)
        List<Map<String, Object>> voteComments = seriesVoteRepository
                .findBySeries_IdOrderByVoteDateDesc(seriesId)
                .stream()
                .map(sv -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("boardName",
                            sv.getBoard() != null && sv.getBoard().getUser() != null
                                    ? sv.getBoard().getUser().getFullname()
                                    : "—");
                    map.put("vote", sv.getVote());
                    map.put("content", sv.getContent());
                    map.put("voteDate", sv.getVoteDate());
                    return map;
                })
                .toList();

        Map<String, Object> seriesInfo = new HashMap<>();
        seriesInfo.put("id", series.getId());
        seriesInfo.put("seriesName", series.getSeriesName());
        seriesInfo.put("description", series.getDescription());
        seriesInfo.put("genre", series.getGenre());
        seriesInfo.put("status", series.getStatus());
        seriesInfo.put("startDate", series.getStartDate());
        seriesInfo.put("bookJacket", series.getBookJacket());

        result.put("status", "success");
        result.put("series", seriesInfo);
        result.put("proposal", proposalInfo);
        result.put("chapterStats", chapterStats);
        result.put("totalViews", totalViews);
        result.put("totalVotes", totalVotes);
        result.put("totalLikes", totalLikes);
        result.put("totalDislikes", totalDislikes);
        result.put("voteSessions", voteSessions);
        result.put("voteComments", voteComments);
        return result;
    }

    @PostMapping("/create")
    @Operation(summary = "[SWAGGER] Tạo series mới")
    @ResponseBody
    public Map<String, Object> createSeries(@RequestParam String seriesName,
            @RequestParam String description) {
        Map<String, Object> result = new HashMap<>();

        try {
            long count = seriesRepository.count();
            String seriesId = String.format("SER%03d", count + 1);

            Series series = new Series();
            series.setId(seriesId);
            series.setSeriesName(seriesName);
            series.setDescription(description);
            series.setStartDate(LocalDate.now());
            series.setStatus("unfinish");
            seriesRepository.save(series);

            result.put("status", "success");
            result.put("seriesId", seriesId);
            result.put("message", "Tạo series thành công!");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }

    @PutMapping("/{seriesId}")
    @Operation(summary = "[SWAGGER] Cập nhật thông tin series")
    @ResponseBody
    public Map<String, Object> updateSeries(@PathVariable String seriesId,
            @RequestParam String seriesName,
            @RequestParam String description) {
        Map<String, Object> result = new HashMap<>();

        Series series = seriesRepository.findById(seriesId).orElse(null);
        if (series == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy series: " + seriesId);
            return result;
        }

        try {
            series.setSeriesName(seriesName);
            series.setDescription(description);
            seriesRepository.save(series);

            result.put("status", "success");
            result.put("seriesId", seriesId);
            result.put("message", "Cập nhật series thành công!");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }

    @DeleteMapping("/{seriesId}")
    @Operation(summary = "[SWAGGER] Xóa một series")
    @ResponseBody
    public Map<String, String> deleteSeries(@PathVariable String seriesId) {
        Map<String, String> result = new HashMap<>();

        if (!seriesRepository.existsById(seriesId)) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy series: " + seriesId);
            return result;
        }

        seriesRepository.deleteById(seriesId);
        result.put("status", "success");
        result.put("message", "Đã xóa series: " + seriesId);
        return result;
    }

    // @GetMapping("/{seriesId}/chapters")
    // @Operation(summary = "[SWAGGER] Lấy danh sách chapter của một series")
    // @ResponseBody
    // public Map<String, Object> getChaptersBySeriesId(@PathVariable String seriesId) {
    //     Map<String, Object> result = new HashMap<>();
    //     Series series = seriesRepository.findById(seriesId).orElse(null);
    //     if (series == null) {
    //         result.put("status", "error");
    //         result.put("message", "Không tìm thấy series: " + seriesId);
    //         return result;
    //     }
    //     List<Chapter> chapters = chapterRepository.findBySeriesId(seriesId);
    //     result.put("status", "success");
    //     result.put("seriesId", seriesId);
    //     result.put("chapters", chapters);
    //     return result;
    // }
    // @PostMapping("/{seriesId}/chapters")
    // @Operation(summary = "[SWAGGER] Tạo chapter mới trong một series")
    // @ResponseBody
    // public Map<String, Object> createChapter(@PathVariable String seriesId,
    //         @RequestParam String chapterName) {
    //     Map<String, Object> result = new HashMap<>();
    //     Series series = seriesRepository.findById(seriesId).orElse(null);
    //     if (series == null) {
    //         result.put("status", "error");
    //         result.put("message", "Không tìm thấy series: " + seriesId);
    //         return result;
    //     }
    //     try {
    //         Optional<Chapter> lastChapter = chapterRepository.findTopByOrderByIdDesc();
    //         int maxId = 0;
    //         if (lastChapter.isPresent()) {
    //             maxId = Integer.parseInt(lastChapter.get().getId().substring(3));
    //         }
    //         String newId = "CHP" + String.format("%03d", maxId + 1);
    //         List<Chapter> chapters = chapterRepository.findBySeriesId(seriesId);
    //         int nextChapterNumber = chapters.size() + 1;
    //         Chapter chapter = new Chapter();
    //         chapter.setId(newId);
    //         chapter.setSeries(series);
    //         chapter.setChapterName(chapterName);
    //         chapter.setChapterNumber(nextChapterNumber);
    //         chapter.setDeadline(resolveNextSaturday(series));
    //         chapter.setStatus("unfinish");
    //         chapterRepository.save(chapter);
    //         result.put("status", "success");
    //         result.put("chapterId", newId);
    //         result.put("chapterNumber", nextChapterNumber);
    //         result.put("message", "Tạo chapter thành công!");
    //     } catch (Exception e) {
    //         result.put("status", "error");
    //         result.put("message", "Lỗi hệ thống: " + e.getMessage());
    //     }
    //     return result;
    // }
    private LocalDate resolveNextSaturday(Series series) {
        Optional<Chapter> latest = chapterRepository.findTopBySeriesOrderByChapterNumberDesc(series);
        if (latest.isPresent() && latest.get().getDeadline() != null) {
            return latest.get().getDeadline().plusWeeks(1);
        }
        return LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
    }
}
