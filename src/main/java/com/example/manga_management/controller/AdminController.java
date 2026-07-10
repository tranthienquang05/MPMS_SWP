package com.example.manga_management.controller;

import com.example.manga_management.entity.*;
import com.example.manga_management.repository.*;

import jakarta.servlet.http.HttpSession;

import java.time.LocalDate;
import java.util.*;
import java.io.InputStream;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/manga/system-admin")
public class AdminController {

    private final UserRepository userRepository;
    private final MangakaRepository mangakaRepository;
    private final AssistantRepository assistantRepository;
    private final TantoEditorRepository tantoEditorRepository;
    private final BoardRepository boardRepository;
    private final SeriesRepository seriesRepository;
    private final VoteSessionRepository voteSessionRepository;
    private final RankingRepository rankingRepository;
    private final EditorialVoteRepository editorialVoteRepository;
    private final SeriesVoteRepository seriesVoteRepository;
    private final SubmissionRepository submissionRepository;
    private final NotificationRepository notificationRepository;
    private final ProposalRepository proposalRepository;
    private final ChapterRepository chapterRepository;
    private final LikeResultRepository likeResultRepository;
    private final PublicDateRepository publicDateRepository;

    public AdminController(UserRepository userRepository,
            MangakaRepository mangakaRepository,
            AssistantRepository assistantRepository,
            TantoEditorRepository tantoEditorRepository,
            BoardRepository boardRepository,
            SeriesRepository seriesRepository,
            VoteSessionRepository voteSessionRepository,
            RankingRepository rankingRepository,
            EditorialVoteRepository editorialVoteRepository,
            SeriesVoteRepository seriesVoteRepository,
            SubmissionRepository submissionRepository,
            NotificationRepository notificationRepository,
            ProposalRepository proposalRepository,
            ChapterRepository chapterRepository,
            LikeResultRepository likeResultRepository,
            PublicDateRepository publicDateRepository) {
        this.userRepository = userRepository;
        this.mangakaRepository = mangakaRepository;
        this.assistantRepository = assistantRepository;
        this.tantoEditorRepository = tantoEditorRepository;
        this.boardRepository = boardRepository;
        this.seriesRepository = seriesRepository;
        this.voteSessionRepository = voteSessionRepository;
        this.rankingRepository = rankingRepository;
        this.editorialVoteRepository = editorialVoteRepository;
        this.seriesVoteRepository = seriesVoteRepository;
        this.submissionRepository = submissionRepository;
        this.notificationRepository = notificationRepository;
        this.proposalRepository = proposalRepository;
        this.chapterRepository = chapterRepository;
        this.likeResultRepository = likeResultRepository;
        this.publicDateRepository = publicDateRepository;
    }

    @GetMapping("")
    public String adminPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"admin".equalsIgnoreCase(user.getRole())) {
            return "redirect:/login";
        }
        model.addAttribute("currentUserId", user.getId());
        return "admin";
    }

    @GetMapping("/data")
    @ResponseBody
    public Map<String, Object> adminData(HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        User user = (User) session.getAttribute("user");
        if (user == null || !"admin".equalsIgnoreCase(user.getRole())) {
            result.put("status", "error");
            result.put("message", "Không có quyền truy cập");
            return result;
        }

        result.put("status", "success");
        result.put("allUsers", userRepository.findAll());
        result.put("tantoEditors", tantoEditorRepository.findAll());
        result.put("boards", boardRepository.findAll());

        // Build mangakas với editor info đầy đủ
        List<Map<String, Object>> mangakaList = new ArrayList<>();
        for (Mangaka m : mangakaRepository.findAll()) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", m.getId());
            item.put("user", m.getUser());
            if (m.getEditor() != null) {
                Map<String, Object> editorMap = new HashMap<>();
                editorMap.put("id", m.getEditor().getId());
                editorMap.put("user", m.getEditor().getUser());
                item.put("editor", editorMap);
            }
            mangakaList.add(item);
        }
        result.put("mangakas", mangakaList);

        // Build assistants với mangaka.user đầy đủ
        List<Map<String, Object>> assistantList = new ArrayList<>();
        for (Assistant a : assistantRepository.findAll()) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", a.getId());
            item.put("user", a.getUser());
            item.put("salaryPerTask", a.getSalaryPerTask());
            if (a.getMangaka() != null) {
                Map<String, Object> mgkMap = new HashMap<>();
                mgkMap.put("id", a.getMangaka().getId());
                mgkMap.put("user", a.getMangaka().getUser());
                item.put("mangaka", mgkMap);
            }
            assistantList.add(item);
        }
        result.put("assistants", assistantList);

        return result;
    }

    @GetMapping("/chapters/pending-publish")
    @ResponseBody
    public Map<String, Object> getChaptersPendingPublish(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null || !"admin".equalsIgnoreCase(user.getRole())) {
            result.put("status", "error");
            result.put("message", "Không có quyền truy cập");
            return result;
        }

        List<Map<String, Object>> chapters = new ArrayList<>();
        for (Chapter c : chapterRepository.findByStatus("pass")) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", c.getId());
            item.put("chapterName", c.getChapterName());
            item.put("chapterNumber", c.getChapterNumber());
            item.put("seriesId", c.getSeries().getId());
            item.put("seriesName", c.getSeries().getSeriesName());
            chapters.add(item);
        }

        result.put("status", "success");
        result.put("chapters", chapters);
        return result;
    }

    @PostMapping("/chapters/{chapterId}/publish")
    @ResponseBody
    public Map<String, Object> publishChapter(@PathVariable String chapterId, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null || !"admin".equalsIgnoreCase(user.getRole())) {
            result.put("status", "error");
            result.put("message", "Không có quyền truy cập");
            return result;
        }

        Chapter chapter = chapterRepository.findById(chapterId).orElse(null);
        if (chapter == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy chapter: " + chapterId);
            return result;
        }
        if (!"pass".equals(chapter.getStatus())) {
            result.put("status", "error");
            result.put("message", "Chỉ có thể xuất bản chapter đã được Tantou duyệt (trạng thái 'pass')");
            return result;
        }

        chapter.setStatus("published");
        chapterRepository.save(chapter);

        long publicCount = publicDateRepository.count();
        PublicDate publicDate = new PublicDate();
        publicDate.setId(String.format("PUB%03d", publicCount + 1));
        publicDate.setChapter(chapter);
        publicDate.setDatePublic(LocalDate.now());
        publicDateRepository.save(publicDate);

        result.put("status", "success");
        result.put("message", "Đã xuất bản chapter '" + chapter.getChapterName() + "'!");
        return result;
    }

    @PostMapping("/accounts/create")
    @ResponseBody
    public Map<String, Object> createAccount(@RequestBody Map<String, String> body,
            HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        try {
            // Kiểm tra quyền
            User admin = (User) session.getAttribute("user");
            if (admin == null || !"admin".equalsIgnoreCase(admin.getRole())) {
                result.put("status", "error");
                result.put("message", "Không có quyền");
                return result;
            }

            // Kiểm tra username trùng
            if (userRepository.findByUsername(body.get("username")).isPresent()) {
                result.put("status", "error");
                result.put("message", "Username đã tồn tại");
                return result;
            }

            // Tạo User
            String newId = String.format("%06d", userRepository.count() + 1);
            User newUser = new User();
            newUser.setId(newId);
            newUser.setUsername(body.get("username"));
            newUser.setPassword(body.get("password"));
            newUser.setFullname(body.getOrDefault("fullname", ""));
            newUser.setEmail(body.getOrDefault("email", ""));
            newUser.setRole(body.get("role"));
            userRepository.save(newUser);

            // Tạo profile tương ứng với role
            String role = body.get("role");
            String profileId = String.format("%06d", getNextProfileId(role));

            switch (role.toLowerCase()) {
                case "mangaka" -> {
                    // Mangaka cần chỉ định TantoEditor
                    String editorId = body.get("editorId");
                    TantoEditor editor = tantoEditorRepository.findById(editorId).orElse(null);
                    if (editor == null) {
                        result.put("status", "error");
                        result.put("message", "Không tìm thấy TantoEditor với ID: " + editorId);
                        return result;
                    }
                    Mangaka mangaka = new Mangaka();
                    mangaka.setId(profileId);
                    mangaka.setUser(newUser);
                    mangaka.setEditor(editor);
                    mangakaRepository.save(mangaka);
                }
                case "assistant" -> {
                    // Assistant cần chỉ định Mangaka
                    String mangakaId = body.get("mangakaId");
                    Mangaka mangaka = mangakaRepository.findById(mangakaId).orElse(null);
                    if (mangaka == null) {
                        result.put("status", "error");
                        result.put("message", "Không tìm thấy Mangaka với ID: " + mangakaId);
                        return result;
                    }
                    Assistant assistant = new Assistant();
                    assistant.setId(profileId);
                    assistant.setUser(newUser);
                    assistant.setMangaka(mangaka);
                    assistant.setSalaryPerTask(
                            Integer.parseInt(body.getOrDefault("salaryPerTask", "0")));
                    assistantRepository.save(assistant);
                }
                case "tantou" -> {
                    TantoEditor editor = new TantoEditor();
                    editor.setId(profileId);
                    editor.setUser(newUser);
                    tantoEditorRepository.save(editor);
                }
                case "board" -> {
                    Board board = new Board();
                    board.setId(profileId);
                    board.setUser(newUser);
                    boardRepository.save(board);
                }
                // "admin" không cần profile riêng
            }

            result.put("status", "success");
            result.put("user", newUser);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    @PostMapping("/accounts/update")
    @ResponseBody
    public Map<String, Object> updateAccount(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            User u = userRepository.findById(body.get("id")).orElse(null);
            if (u == null) {
                result.put("status", "error");
                result.put("message", "Không tìm thấy user với ID: " + body.get("id"));
                return result;
            }

            if (isSet(body, "username"))
                u.setUsername(body.get("username"));
            if (isSet(body, "fullname"))
                u.setFullname(body.get("fullname"));
            if (isSet(body, "email"))
                u.setEmail(body.get("email"));
            if (isSet(body, "password"))
                u.setPassword(body.get("password"));
            // Không cho đổi role vì liên quan đến profile table

            userRepository.save(u);

            // Nếu là assistant và muốn đổi mangaka
            if (isSet(body, "mangakaId")) {
                Assistant assistant = assistantRepository.findByUserId(u.getId()).orElse(null);
                Mangaka mangaka = mangakaRepository.findById(body.get("mangakaId")).orElse(null);
                if (assistant != null && mangaka != null) {
                    assistant.setMangaka(mangaka);
                    if (isSet(body, "salaryPerTask")) {
                        assistant.setSalaryPerTask(Integer.parseInt(body.get("salaryPerTask")));
                    }
                    assistantRepository.save(assistant);
                }
            }

            // Nếu là mangaka và muốn đổi tantou
            if (isSet(body, "editorId")) {
                Mangaka mangaka = mangakaRepository.findByUserId(u.getId()).orElse(null);
                TantoEditor editor = tantoEditorRepository.findById(body.get("editorId")).orElse(null);
                if (mangaka != null && editor != null) {
                    mangaka.setEditor(editor);
                    mangakaRepository.save(mangaka);
                }
            }

            result.put("status", "success");
            result.put("user", u);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    @PostMapping("/accounts/delete")
    @ResponseBody
    public Map<String, Object> deleteAccount(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            String id = body.get("id");
            User u = userRepository.findById(id).orElse(null);
            if (u == null) {
                result.put("status", "error");
                result.put("message", "Không tìm thấy user với ID: " + id);
                return result;
            }

            // Xóa profile trước, sau đó xóa user
            switch (u.getRole().toLowerCase()) {
                case "mangaka" -> mangakaRepository.findByUserId(id)
                        .ifPresent(mangakaRepository::delete);
                case "assistant" -> assistantRepository.findByUserId(id)
                        .ifPresent(assistantRepository::delete);
                case "tantou" -> tantoEditorRepository.findByUserId(id)
                        .ifPresent(tantoEditorRepository::delete);
                case "board" -> boardRepository.findByUser_Id(id)
                        .ifPresent(boardRepository::delete);
            }

            userRepository.deleteById(id);
            result.put("status", "success");
            result.put("deletedId", id);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    // ══════════════════════════════════════════════════════════════
    // ĐỔI QUAN HỆ (riêng biệt với update thông tin cơ bản)
    // ══════════════════════════════════════════════════════════════

    // Đổi Mangaka của Assistant
    @PostMapping("/assign/assistant-mangaka")
    @ResponseBody
    public Map<String, Object> assignAssistantToMangaka(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            Assistant assistant = assistantRepository.findById(body.get("assistantId")).orElse(null);
            Mangaka mangaka = mangakaRepository.findById(body.get("mangakaId")).orElse(null);

            if (assistant == null) {
                result.put("status", "error");
                result.put("message", "Không tìm thấy Assistant");
                return result;
            }
            if (mangaka == null) {
                result.put("status", "error");
                result.put("message", "Không tìm thấy Mangaka");
                return result;
            }

            assistant.setMangaka(mangaka);
            assistantRepository.save(assistant);

            result.put("status", "success");
            result.put("assistantId", assistant.getId());
            result.put("assistantName", assistant.getUser().getFullname());
            result.put("mangakaId", mangaka.getId());
            result.put("mangakaName", mangaka.getUser().getFullname());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    // Đổi TantoEditor của Mangaka
    @PostMapping("/assign/mangaka-tanto")
    @ResponseBody
    public Map<String, Object> assignMangakaToTanto(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            Mangaka mangaka = mangakaRepository.findById(body.get("mangakaId")).orElse(null);
            TantoEditor editor = tantoEditorRepository.findById(body.get("editorId")).orElse(null);

            if (mangaka == null) {
                result.put("status", "error");
                result.put("message", "Không tìm thấy Mangaka");
                return result;
            }
            if (editor == null) {
                result.put("status", "error");
                result.put("message", "Không tìm thấy TantoEditor");
                return result;
            }

            mangaka.setEditor(editor);
            mangakaRepository.save(mangaka);

            result.put("status", "success");
            result.put("mangakaId", mangaka.getId());
            result.put("mangakaName", mangaka.getUser().getFullname());
            result.put("editorId", editor.getId());
            result.put("editorName", editor.getUser().getFullname());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    // ════════════════════════════════════════════════════════════
    //  QUẢN LÝ VOTE SERIES (Admin tạo phiên, Board cast vote)
    // ════════════════════════════════════════════════════════════

    // Lấy danh sách series cho dropdown khi tạo phiên
    @GetMapping("/ranking/all-series")
    @ResponseBody
    public Map<String, Object> getAllSeriesForVote(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null || !"admin".equalsIgnoreCase(user.getRole())) {
            result.put("status", "error");
            result.put("message", "Không có quyền truy cập");
            return result;
        }

        List<Object[]> rows = rankingRepository.findAllSeriesDistinct();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", row[0]);
            map.put("name", row[1]);
            map.put("status", row[2]);
            list.add(map);
        }
        result.put("status", "success");
        result.put("series", list);
        return result;
    }

    // Tạo phiên vote thủ công (Admin chọn series + loại vote)
    @PostMapping("/ranking/create-session")
    @ResponseBody
    public Map<String, Object> createVoteSession(
            @RequestParam String seriesId,
            @RequestParam String voteType,
            HttpSession session) {

        Map<String, Object> response = new LinkedHashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null || !"admin".equalsIgnoreCase(user.getRole())) {
            response.put("success", false);
            response.put("message", "Không có quyền truy cập");
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
        vs.setCreatedBy(null); // Admin tạo, không phải Board
        vs.setVoteType(voteType);
        vs.setStatus("active");
        vs.setCreatedAt(LocalDate.now());
        vs.setAutoCreated(false);
        voteSessionRepository.save(vs);

        response.put("success", true);
        response.put("sessionId", vs.getId());
        response.put("message", "Đã tạo phiên vote " + (voteType.equals("stop") ? "dừng" : "khen thưởng")
                + " cho series \"" + seriesOpt.get().getSeriesName() + "\"!");
        return response;
    }

    // Tạo phiên vote dừng cho 3 series cuối bảng
    @PostMapping("/ranking/create-bottom-sessions")
    @ResponseBody
    public Map<String, Object> createBottomVoteSessions(
            @RequestParam(defaultValue = "0") int month,
            @RequestParam(defaultValue = "0") int quarter,
            @RequestParam(defaultValue = "2026") int year,
            HttpSession session) {

        Map<String, Object> response = new LinkedHashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null || !"admin".equalsIgnoreCase(user.getRole())) {
            response.put("success", false);
            response.put("message", "Không có quyền truy cập");
            return response;
        }

        try {
            List<Object[]> rows;
            if (quarter >= 1 && quarter <= 4) {
                List<Integer> months = getMonthsForQuarter(quarter);
                rows = rankingRepository.findBottomByQuarterAndYear(months, year);
            } else if (month > 0) {
                rows = rankingRepository.findBottomByMonthAndYear(month, year);
            } else {
                rows = rankingRepository.findBottomByYear(year);
            }

            int count = Math.min(3, rows.size());
            List<String> createdNames = new ArrayList<>();
            List<String> skippedNames = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                String sid = (String) rows.get(i)[0];
                String sname = (String) rows.get(i)[1];

                Optional<Series> seriesOpt = seriesRepository.findById(sid);
                if (seriesOpt.isEmpty()) continue;
                String seriesStatus = seriesOpt.get().getStatus();

                if ("stopped".equals(seriesStatus) || "rewarded".equals(seriesStatus)
                        || voteSessionRepository.existsBySeriesIdAndVoteType(sid, "stop")) {
                    skippedNames.add(sname);
                    continue;
                }

                VoteSession vs = new VoteSession();
                vs.setId(generateSessionId());
                vs.setSeries(seriesOpt.get());
                vs.setCreatedBy(null); // Admin tạo
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

    // ════════════════════════════════════════════════════════════
    //  LỊCH SỬ HOẠT ĐỘNG USER (Admin xem)
    // ════════════════════════════════════════════════════════════

    // Search user theo keyword (fullname / username / email / id)
    @GetMapping("/users/search")
    @ResponseBody
    public Map<String, Object> searchUsers(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "") String keyword,
            HttpSession session) {

        Map<String, Object> result = new HashMap<>();
        User admin = (User) session.getAttribute("user");
        if (admin == null || !"admin".equalsIgnoreCase(admin.getRole())) {
            result.put("status", "error");
            result.put("message", "Không có quyền truy cập");
            return result;
        }

        String kw = keyword.trim().toLowerCase();
        List<Map<String, Object>> list = new ArrayList<>();
        for (User u : userRepository.findAll()) {
            boolean match = kw.isEmpty()
                    || (u.getId() != null && u.getId().toLowerCase().contains(kw))
                    || (u.getFullname() != null && u.getFullname().toLowerCase().contains(kw))
                    || (u.getUsername() != null && u.getUsername().toLowerCase().contains(kw))
                    || (u.getEmail() != null && u.getEmail().toLowerCase().contains(kw));
            if (!match) continue;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("fullname", u.getFullname());
            m.put("username", u.getUsername());
            m.put("email", u.getEmail());
            m.put("role", u.getRole());
            list.add(m);
        }

        result.put("status", "success");
        result.put("users", list);
        return result;
    }

    // Lấy lịch sử hoạt động của 1 user — tổng hợp từ nhiều bảng có sẵn
    @GetMapping("/users/{userId}/activity")
    @ResponseBody
    public Map<String, Object> getUserActivity(
            @org.springframework.web.bind.annotation.PathVariable String userId,
            HttpSession session) {

        Map<String, Object> result = new HashMap<>();
        User admin = (User) session.getAttribute("user");
        if (admin == null || !"admin".equalsIgnoreCase(admin.getRole())) {
            result.put("status", "error");
            result.put("message", "Không có quyền truy cập");
            return result;
        }

        User target = userRepository.findById(userId).orElse(null);
        if (target == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy user");
            return result;
        }

        List<Map<String, Object>> activities = new ArrayList<>();
        String role = target.getRole() != null ? target.getRole().toLowerCase() : "";

        // 1. Board: vote proposal (EditorialVote)
        if ("board".equals(role)) {
            for (EditorialVote ev : editorialVoteRepository.findByBoard_User_IdOrderByVoteDateDesc(userId)) {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("timestamp", ev.getVoteDate() != null ? ev.getVoteDate().toString() : "");
                a.put("type", "vote-proposal");
                a.put("icon", "fa-file-circle-check");
                String label = "pass".equalsIgnoreCase(ev.getVote()) ? "✅ Chấp thuận" : "❌ Bác bỏ";
                a.put("description", "Bỏ phiếu " + label + " bản thảo "
                        + (ev.getProposal() != null ? ev.getProposal().getSeriesName() : "") + " ("
                        + (ev.getProposal() != null ? ev.getProposal().getId() : "") + ")");
                activities.add(a);
            }

            // Board: vote series (SeriesVote)
            for (SeriesVote sv : seriesVoteRepository.findByBoard_User_IdOrderByVoteDateDesc(userId)) {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("timestamp", sv.getVoteDate() != null ? sv.getVoteDate().toString() : "");
                a.put("type", "vote-series");
                a.put("icon", "fa-square-poll-vertical");
                String choice = sv.getVote() == null ? "" : sv.getVote();
                String label = switch (choice.toLowerCase()) {
                    case "stop" -> "🚫 Dừng";
                    case "keep" -> "✅ Giữ";
                    case "reward" -> "🏆 Đồng ý khen thưởng";
                    case "against" -> "❌ Không đồng ý";
                    default -> choice;
                };
                a.put("description", "Vote " + label + " cho series "
                        + (sv.getSeries() != null ? sv.getSeries().getSeriesName() : "") + " ("
                        + (sv.getSeries() != null ? sv.getSeries().getId() : "") + ")");
                activities.add(a);
            }

            // Board: tạo phiên vote (VoteSession.createdBy)
            for (VoteSession vs : voteSessionRepository.findByCreatedBy_User_IdOrderByCreatedAtDesc(userId)) {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("timestamp", vs.getCreatedAt() != null ? vs.getCreatedAt().toString() : "");
                a.put("type", "create-session");
                a.put("icon", "fa-plus-square");
                String t = "stop".equalsIgnoreCase(vs.getVoteType()) ? "dừng" : "khen thưởng";
                a.put("description", "Tạo phiên vote " + t + " " + vs.getId() + " cho series "
                        + (vs.getSeries() != null ? vs.getSeries().getSeriesName() : ""));
                activities.add(a);
            }
        }

        // 2. Mangaka: tạo / nộp proposal + giao task + chấp nhận submit
        if ("mangaka".equals(role)) {
            Optional<Mangaka> mgkOpt = mangakaRepository.findByUserId(userId);
            if (mgkOpt.isPresent()) {
                String mangakaId = mgkOpt.get().getId();

                // 2a. Proposal (bản thảo)
                for (Proposal p : proposalRepository.findByMangaka_Id(mangakaId)) {
                    Map<String, Object> a = new LinkedHashMap<>();
                    a.put("timestamp", p.getCreatedAt() != null ? p.getCreatedAt().toString() : "");
                    a.put("type", "proposal");
                    a.put("icon", "fa-file-pen");
                    String statusLabel = switch (p.getStatus() != null ? p.getStatus().toLowerCase() : "") {
                        case "pending" -> "⏳ Chờ duyệt";
                        case "approved" -> "✅ Đã duyệt";
                        case "rejected" -> "❌ Bị từ chối";
                        case "resubmit" -> "🔄 Cần chỉnh sửa";
                        default -> p.getStatus() != null ? p.getStatus() : "";
                    };
                    a.put("description", "Bản thảo \"" + p.getSeriesName() + "\" (" + p.getId()
                            + ") — " + statusLabel);
                    activities.add(a);
                }

                // 2b. Giao task + chấp nhận submit (qua Submission của các assistant)
                for (Submission s : submissionRepository.findByAssistant_Mangaka_IdOrderByCreatedAtDesc(mangakaId)) {
                    String assistantName = s.getAssistant() != null && s.getAssistant().getUser() != null
                            ? s.getAssistant().getUser().getFullname() : "—";
                    String pageInfo = "";
                    if (s.getPageId() != null) {
                        MangaPage pg = s.getPageId();
                        String chapterName = pg.getChapter() != null ? pg.getChapter().getChapterName() : "—";
                        pageInfo = "trang " + (pg.getPageNumber() != null ? pg.getPageNumber() : s.getId())
                                + " (" + chapterName + ")";
                    }

                    // Giao task: dùng createdAt
                    if (s.getCreatedAt() != null) {
                        Map<String, Object> a = new LinkedHashMap<>();
                        a.put("timestamp", s.getCreatedAt().toString());
                        a.put("type", "assign-task");
                        a.put("icon", "fa-list-check");
                        a.put("description", "Giao " + pageInfo + " cho " + assistantName
                                + " (deadline " + (s.getDeadline() != null ? s.getDeadline().toString() : "—") + ")");
                        activities.add(a);
                    }

                    // Chấp nhận submit: dùng approvedAt
                    if (s.getApprovedAt() != null) {
                        Map<String, Object> a = new LinkedHashMap<>();
                        a.put("timestamp", s.getApprovedAt().toString());
                        a.put("type", "approve-task");
                        a.put("icon", "fa-circle-check");
                        a.put("description", "Duyệt bài " + pageInfo + " của " + assistantName);
                        activities.add(a);
                    }
                }
            }
        }

        // 3. Assistant: nhận / nộp task (Submission)
        if ("assistant".equals(role)) {
            Optional<Assistant> astOpt = assistantRepository.findByUserId(userId);
            if (astOpt.isPresent()) {
                for (Submission s : submissionRepository.findByAssistant_Id(astOpt.get().getId())) {
                    Map<String, Object> a = new LinkedHashMap<>();
                    a.put("timestamp", s.getCreatedAt() != null ? s.getCreatedAt().toString() : "");
                    a.put("type", "submission");
                    a.put("icon", "fa-list-check");
                    String statusLabel = switch (s.getStatus() != null ? s.getStatus().toLowerCase() : "") {
                        case "pending" -> "⏳ Chờ nộp";
                        case "submitted" -> "📤 Đã nộp";
                        case "approved" -> "✅ Đã duyệt";
                        case "rejected" -> "❌ Bị từ chối";
                        case "late" -> "🕐 Nộp trễ";
                        default -> s.getStatus() != null ? s.getStatus() : "";
                    };
                    a.put("description", "Task " + s.getId()
                            + " (deadline " + (s.getDeadline() != null ? s.getDeadline().toString() : "—")
                            + ") — " + statusLabel);
                    activities.add(a);
                }
            }
        }

        // 4. TantoEditor: quản lý proposal của các mangaka trực thuộc
        if ("tantou".equals(role)) {
            Optional<TantoEditor> editorOpt = tantoEditorRepository.findByUserId(userId);
            if (editorOpt.isPresent()) {
                for (Proposal p : proposalRepository.findByMangaka_Editor_IdOrderByCreatedAtDesc(editorOpt.get().getId())) {
                    Map<String, Object> a = new LinkedHashMap<>();
                    a.put("timestamp", p.getCreatedAt() != null ? p.getCreatedAt().toString() : "");
                    a.put("type", "manage-proposal");
                    a.put("icon", "fa-file-circle-check");
                    String statusLabel = switch (p.getStatus() != null ? p.getStatus().toLowerCase() : "") {
                        case "pending" -> "⏳ Chờ duyệt";
                        case "approved" -> "✅ Đã duyệt";
                        case "rejected" -> "❌ Bị từ chối";
                        case "resubmit" -> "🔄 Cần chỉnh sửa";
                        default -> p.getStatus() != null ? p.getStatus() : "";
                    };
                    String mangakaName = p.getMangaka() != null && p.getMangaka().getUser() != null
                            ? p.getMangaka().getUser().getFullname() : "—";
                    a.put("description", "Bản thảo \"" + p.getSeriesName() + "\" (" + p.getId()
                            + ") của " + mangakaName + " — " + statusLabel);
                    activities.add(a);
                }
            }
        }

        // 5. Notification — áp dụng cho mọi role (đã có createdAt LocalDateTime)
        for (Notification n : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("timestamp", n.getCreatedAt() != null ? n.getCreatedAt().toString() : "");
            a.put("type", "notification");
            a.put("icon", "fa-bell");
            a.put("description", n.getContent());
            activities.add(a);
        }

        // Sort theo timestamp DESC (chuỗi rỗng đẩy xuống cuối)
        activities.sort((a, b) -> {
            String ta = (String) a.get("timestamp");
            String tb = (String) b.get("timestamp");
            if (ta == null) ta = "";
            if (tb == null) tb = "";
            if (ta.isEmpty() && tb.isEmpty()) return 0;
            if (ta.isEmpty()) return 1;
            if (tb.isEmpty()) return -1;
            return tb.compareTo(ta);
        });

        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("id", target.getId());
        userInfo.put("fullname", target.getFullname());
        userInfo.put("username", target.getUsername());
        userInfo.put("email", target.getEmail());
        userInfo.put("role", target.getRole());

        result.put("status", "success");
        result.put("user", userInfo);
        result.put("activities", activities);
        return result;
    }

    // ── Helper ────────────────────────────────────────────────────
    private List<Integer> getMonthsForQuarter(int quarter) {
        int start = (quarter - 1) * 3 + 1;
        return List.of(start, start + 1, start + 2);
    }

    private boolean isSet(Map<String, String> body, String key) {
        return body.containsKey(key) && body.get(key) != null && !body.get(key).isBlank();
    }

    private long getNextProfileId(String role) {
        return switch (role.toLowerCase()) {
            case "mangaka" -> mangakaRepository.count() + 1;
            case "assistant" -> assistantRepository.count() + 1;
            case "tantou" -> tantoEditorRepository.count() + 1;
            case "board" -> boardRepository.count() + 1;
            default -> 0;
        };
    }

    // ════════════════════════════════════════════════════════════
    // TÍNH NĂNG 3: IMPORT EXCEL
    // ════════════════════════════════════════════════════════════
    @PostMapping("/import-excel")
    @ResponseBody
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importExcel(@RequestParam("file") MultipartFile file, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null || !"admin".equalsIgnoreCase(user.getRole())) {
            result.put("status", "error");
            result.put("message", "Không có quyền truy cập");
            return result;
        }

        if (file.isEmpty() || file.getOriginalFilename() == null || !file.getOriginalFilename().endsWith(".xlsx")) {
            result.put("status", "error");
            result.put("message", "file phải là .xlsx");
            return result;
        }

        try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet viewSheet = workbook.getSheet("ViewCount");
            Sheet likeSheet = workbook.getSheet("LikeDislike");
            
            if (viewSheet == null || likeSheet == null) {
                result.put("status", "error");
                result.put("message", "File phải có đủ 2 sheet: 'ViewCount' và 'LikeDislike'");
                return result;
            }

            List<String> errors = new ArrayList<>();

            // ============================================
            // BƯỚC 1: VALIDATE TOÀN BỘ FILE
            // ============================================
            
            // Validate sheet ViewCount
            for (Row row : viewSheet) {
                if (row.getRowNum() == 0) continue; // Skip header

                String chapterId = getCellStringValue(row.getCell(0));
                String viewStr = getCellStringValue(row.getCell(1));

                if (chapterId.isEmpty() && viewStr.isEmpty()) continue; // Skip empty rows
                
                if (chapterId.isEmpty()) {
                    errors.add("Sheet ViewCount, dòng " + (row.getRowNum() + 1) + ": ChapterID không được để trống");
                } else {
                    Optional<Chapter> chapterOpt = chapterRepository.findById(chapterId);
                    if (chapterOpt.isEmpty()) {
                        errors.add("Sheet ViewCount, dòng " + (row.getRowNum() + 1) + ": ChapterID '" + chapterId + "' không tồn tại");
                    }
                }

                if (viewStr.isEmpty()) {
                    errors.add("Sheet ViewCount, dòng " + (row.getRowNum() + 1) + ": ViewCount không được để trống");
                } else {
                    try {
                        double parsed = Double.parseDouble(viewStr.replace(",", ""));
                        if (parsed % 1 != 0) {
                            errors.add("Sheet ViewCount, dòng " + (row.getRowNum() + 1) + ": ViewCount phải là số nguyên");
                        } else {
                            int viewCount = (int) parsed;
                            if (viewCount < 0) {
                                errors.add("Sheet ViewCount, dòng " + (row.getRowNum() + 1) + ": ViewCount không được âm");
                            }
                        }
                    } catch (NumberFormatException e) {
                        errors.add("Sheet ViewCount, dòng " + (row.getRowNum() + 1) + ": ViewCount phải là số nguyên");
                    }
                }
            }

            // Validate sheet LikeDislike
            for (Row row : likeSheet) {
                if (row.getRowNum() == 0) continue; // Skip header

                String seriesId = getCellStringValue(row.getCell(0));
                String monthStr = getCellStringValue(row.getCell(1));
                String yearStr = getCellStringValue(row.getCell(2));
                String likeStr = getCellStringValue(row.getCell(3));
                String dislikeStr = getCellStringValue(row.getCell(4));

                if (seriesId.isEmpty() && monthStr.isEmpty() && yearStr.isEmpty() && likeStr.isEmpty() && dislikeStr.isEmpty()) continue;

                if (seriesId.isEmpty()) {
                    errors.add("Sheet LikeDislike, dòng " + (row.getRowNum() + 1) + ": SeriesID không được để trống");
                } else {
                    Optional<Series> seriesOpt = seriesRepository.findById(seriesId);
                    if (seriesOpt.isEmpty()) {
                        errors.add("Sheet LikeDislike, dòng " + (row.getRowNum() + 1) + ": SeriesID '" + seriesId + "' không tồn tại");
                    }
                }

                if (monthStr.isEmpty()) {
                    errors.add("Sheet LikeDislike, dòng " + (row.getRowNum() + 1) + ": Month không được để trống");
                } else {
                    try {
                        double parsed = Double.parseDouble(monthStr.replace(",", ""));
                        if (parsed % 1 != 0) {
                            errors.add("Sheet LikeDislike, dòng " + (row.getRowNum() + 1) + ": Month phải là số nguyên");
                        } else {
                            int month = (int) parsed;
                            if (month < 1 || month > 12) {
                                errors.add("Sheet LikeDislike, dòng " + (row.getRowNum() + 1) + ": Month = " + month + " không hợp lệ (phải từ 1-12)");
                            }
                        }
                    } catch (NumberFormatException e) {
                        errors.add("Sheet LikeDislike, dòng " + (row.getRowNum() + 1) + ": Month phải là số nguyên");
                    }
                }

                if (yearStr.isEmpty()) {
                    errors.add("Sheet LikeDislike, dòng " + (row.getRowNum() + 1) + ": Year không được để trống");
                } else {
                    try {
                        double parsed = Double.parseDouble(yearStr.replace(",", ""));
                        if (parsed % 1 != 0) {
                            errors.add("Sheet LikeDislike, dòng " + (row.getRowNum() + 1) + ": Year phải là số nguyên");
                        } else {
                            int year = (int) parsed;
                            if (year <= 2000) {
                                errors.add("Sheet LikeDislike, dòng " + (row.getRowNum() + 1) + ": Year = " + year + " không hợp lệ (phải > 2000)");
                            }
                        }
                    } catch (NumberFormatException e) {
                        errors.add("Sheet LikeDislike, dòng " + (row.getRowNum() + 1) + ": Year phải là số nguyên");
                    }
                }

                if (likeStr.isEmpty()) {
                    errors.add("Sheet LikeDislike, dòng " + (row.getRowNum() + 1) + ": LikeNumber không được để trống");
                } else {
                    try {
                        double parsed = Double.parseDouble(likeStr.replace(",", ""));
                        if (parsed % 1 != 0) {
                            errors.add("Sheet LikeDislike, dòng " + (row.getRowNum() + 1) + ": LikeNumber phải là số nguyên");
                        } else {
                            int likes = (int) parsed;
                            if (likes < 0) {
                                errors.add("Sheet LikeDislike, dòng " + (row.getRowNum() + 1) + ": LikeNumber không được âm");
                            }
                        }
                    } catch (NumberFormatException e) {
                        errors.add("Sheet LikeDislike, dòng " + (row.getRowNum() + 1) + ": LikeNumber phải là số nguyên");
                    }
                }

                if (dislikeStr.isEmpty()) {
                    errors.add("Sheet LikeDislike, dòng " + (row.getRowNum() + 1) + ": DislikeNumber không được để trống");
                } else {
                    try {
                        double parsed = Double.parseDouble(dislikeStr.replace(",", ""));
                        if (parsed % 1 != 0) {
                            errors.add("Sheet LikeDislike, dòng " + (row.getRowNum() + 1) + ": DislikeNumber phải là số nguyên");
                        } else {
                            int dislikes = (int) parsed;
                            if (dislikes < 0) {
                                errors.add("Sheet LikeDislike, dòng " + (row.getRowNum() + 1) + ": DislikeNumber không được âm");
                            }
                        }
                    } catch (NumberFormatException e) {
                        errors.add("Sheet LikeDislike, dòng " + (row.getRowNum() + 1) + ": DislikeNumber phải là số nguyên");
                    }
                }
            }

            if (!errors.isEmpty()) {
                result.put("status", "error");
                result.put("errors", errors);
                return result;
            }

            // ============================================
            // BƯỚC 2: LƯU VÀO DB NẾU VALIDATE PASS
            // ============================================
            int updatedViewCount = 0;
            for (Row row : viewSheet) {
                if (row.getRowNum() == 0) continue;
                String chapterId = getCellStringValue(row.getCell(0));
                String viewStr = getCellStringValue(row.getCell(1));
                if (chapterId.isEmpty() && viewStr.isEmpty()) continue;
                
                int viewCount = (int) Double.parseDouble(viewStr.replace(",", ""));
                
                Chapter chapter = chapterRepository.findById(chapterId).get();
                chapter.setViewCount(viewCount);
                chapterRepository.save(chapter);
                updatedViewCount++;
            }

            int updatedLikeCount = 0;
            for (Row row : likeSheet) {
                if (row.getRowNum() == 0) continue;
                String seriesId = getCellStringValue(row.getCell(0));
                String monthStr = getCellStringValue(row.getCell(1));
                String yearStr = getCellStringValue(row.getCell(2));
                String likeStr = getCellStringValue(row.getCell(3));
                String dislikeStr = getCellStringValue(row.getCell(4));
                if (seriesId.isEmpty() && monthStr.isEmpty() && yearStr.isEmpty() && likeStr.isEmpty() && dislikeStr.isEmpty()) continue;

                int month = (int) Double.parseDouble(monthStr.replace(",", ""));
                int year = (int) Double.parseDouble(yearStr.replace(",", ""));
                int likes = (int) Double.parseDouble(likeStr.replace(",", ""));
                int dislikes = (int) Double.parseDouble(dislikeStr.replace(",", ""));

                Optional<LikeResult> lrOpt = likeResultRepository.findBySeriesIdAndMonthAndYear(seriesId, month, year);
                if (lrOpt.isPresent()) {
                    LikeResult lr = lrOpt.get();
                    lr.setLikeNumber(likes);
                    lr.setDislikeNumber(dislikes);
                    likeResultRepository.save(lr);
                } else {
                    LikeResult lr = new LikeResult();
                    lr.setId(UUID.randomUUID().toString().substring(0, 7));
                    Series series = seriesRepository.findById(seriesId).get();
                    lr.setSeries(series);
                    lr.setLikeNumber(likes);
                    lr.setDislikeNumber(dislikes);
                    lr.setMonth(month);
                    lr.setYear(year);
                    likeResultRepository.save(lr);
                }
                updatedLikeCount++;
            }

            result.put("status", "success");
            result.put("message", "Import thành công " + updatedViewCount + " dòng chapter view, " + updatedLikeCount + " dòng like/dislike");
        } catch (Exception e) {
            e.printStackTrace();
            result.put("status", "error");
            result.put("message", "Lỗi xử lý Excel: " + e.getMessage());
        }

        return result;
    }

    private String getCellStringValue(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return "";
        org.apache.poi.ss.usermodel.DataFormatter formatter = new org.apache.poi.ss.usermodel.DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }

    private int getCellIntValue(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return 0;
        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
            return (int) cell.getNumericCellValue();
        } else if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.FORMULA) {
            try {
                return (int) cell.getNumericCellValue();
            } catch (Exception e) {
                return 0;
            }
        } else {
            org.apache.poi.ss.usermodel.DataFormatter formatter = new org.apache.poi.ss.usermodel.DataFormatter();
            String val = formatter.formatCellValue(cell).trim();
            if (val.isEmpty()) return 0;
            try {
                return (int) Double.parseDouble(val); // Handle cases like "1.0"
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }

    @GetMapping("/download-template")
    public org.springframework.http.ResponseEntity<byte[]> downloadTemplate() {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet viewSheet = workbook.createSheet("ViewCount");
            Row vcHeaderRow = viewSheet.createRow(0);
            vcHeaderRow.createCell(0).setCellValue("ChapterID");
            vcHeaderRow.createCell(1).setCellValue("ViewCount");
            
            Row vcDataRow = viewSheet.createRow(1);
            vcDataRow.createCell(0).setCellValue("CPT0001");
            vcDataRow.createCell(1).setCellValue(1500);

            Sheet likeSheet = workbook.createSheet("LikeDislike");
            Row ldHeaderRow = likeSheet.createRow(0);
            ldHeaderRow.createCell(0).setCellValue("SeriesID");
            ldHeaderRow.createCell(1).setCellValue("Month");
            ldHeaderRow.createCell(2).setCellValue("Year");
            ldHeaderRow.createCell(3).setCellValue("LikeNumber");
            ldHeaderRow.createCell(4).setCellValue("DislikeNumber");
            
            Row ldDataRow = likeSheet.createRow(1);
            ldDataRow.createCell(0).setCellValue("SER001");
            ldDataRow.createCell(1).setCellValue(1);
            ldDataRow.createCell(2).setCellValue(2026);
            ldDataRow.createCell(3).setCellValue(1000);
            ldDataRow.createCell(4).setCellValue(200);

            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            workbook.write(out);

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.add("Content-Disposition", "attachment; filename=template_import_manga.xlsx");
            return new org.springframework.http.ResponseEntity<>(out.toByteArray(), headers, org.springframework.http.HttpStatus.OK);
        } catch (Exception e) {
            return new org.springframework.http.ResponseEntity<>(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
