package com.example.manga_management.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.example.manga_management.entity.Assistant;
import com.example.manga_management.entity.Chapter;
import com.example.manga_management.entity.MangaPage;
import com.example.manga_management.entity.Mangaka;
import com.example.manga_management.entity.Proposal;
import com.example.manga_management.entity.Series;
import com.example.manga_management.entity.Submission;
import com.example.manga_management.entity.User;
import com.example.manga_management.repository.AssistantRepository;
import com.example.manga_management.repository.MangaPageRepository;
import com.example.manga_management.repository.MangakaRepository;
import com.example.manga_management.repository.ProposalRepository;
import com.example.manga_management.repository.SeriesRepository;
import com.example.manga_management.repository.SubmissionRepository;
import com.example.manga_management.service.ProposalService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;

@Tag(name = "Mangaka Controller", description = "Endpoints for Mangaka operations, including project submission, series management, and chapter creation.")
@Controller
@RequestMapping("/manga/mangaka")
public class MangakaController {

    private final SubmissionRepository submissionRepository;
    private final ProposalRepository proposalRepository;
    private final MangakaRepository mangakaRepository;
    private final SeriesRepository seriesRepository;
    private final com.example.manga_management.repository.ChapterRepository chapterRepository;
    private final MangaPageRepository mangaPageRepository;
    private final AssistantRepository assistantRepository;
    private final ProposalService proposalService;
    private NotificationController notificationController;

    public MangakaController(ProposalRepository proposalRepository, MangakaRepository mangakaRepository,
            SeriesRepository seriesRepository,
            com.example.manga_management.repository.ChapterRepository chapterRepository,
            MangaPageRepository mangaPageRepository, SubmissionRepository submissionRepository,
            NotificationController notificationController, AssistantRepository assistantRepository,
            ProposalService proposalService) {
        this.proposalRepository = proposalRepository;
        this.mangakaRepository = mangakaRepository;
        this.assistantRepository = assistantRepository;
        this.seriesRepository = seriesRepository;
        this.chapterRepository = chapterRepository;
        this.mangaPageRepository = mangaPageRepository;
        this.submissionRepository = submissionRepository;
        this.notificationController = notificationController;
        this.proposalService = proposalService;
    }

    @GetMapping({""})
    public String mangakaPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("currentUserId", user.getId());
        Mangaka mangaka = mangakaRepository.findByUser(user).orElse(null);
        model.addAttribute("mangaka", mangaka);
        if (mangaka != null) {
            model.addAttribute("allProposals", proposalRepository.findByMangaka_Id(mangaka.getId()));
            model.addAttribute("approvedList",
                    proposalRepository.findByStatusInAndMangaka_Id(List.of("approved", "board_check", "passed"),
                            mangaka.getId()));
            model.addAttribute("revisionList",
                    proposalRepository.findByStatusAndMangaka_Id("revision", mangaka.getId()));
            model.addAttribute("lockedList",
                    proposalRepository.findByStatusAndMangaka_Id("locked", mangaka.getId()));
        }
        return "mangaka";
    }

    // ===================== SWAGGER / JSON ENDPOINTS =====================
    @Operation(summary = "[SWAGGER] Xem tất cả proposals của một Mangaka")
    @GetMapping("/my-projects/data")
    @ResponseBody
    public Map<String, Object> myProjectsData(@RequestParam String mangakaId, Model model) {
        Map<String, Object> result = new HashMap<>();

        Mangaka mangaka = mangakaRepository.findById(mangakaId).orElse(null);
        if (mangaka == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy Mangaka với ID: " + mangakaId);
            return result;
        }

        model.addAttribute("allProposals", proposalRepository.findByMangaka_Id(mangaka.getId()));
        model.addAttribute("approvedList",
                proposalRepository.findByStatusInAndMangaka_Id(List.of("approved", "board_check", "passed"),
                        mangaka.getId()));
        model.addAttribute("revisionList",
                proposalRepository.findByStatusAndMangaka_Id("revision", mangaka.getId()));
        model.addAttribute("lockedList",
                proposalRepository.findByStatusAndMangaka_Id("locked", mangaka.getId()));

        result.put("status", "success");
        result.put("allProposals",
                proposalRepository.findByMangaka_Id(mangaka.getId()));
        result.put("approvedList",
                proposalRepository.findByStatusInAndMangaka_Id(List.of("approved", "board_check", "passed"),
                        mangaka.getId()));
        result.put("revisionList",
                proposalRepository.findByStatusAndMangaka_Id("revision", mangaka.getId()));
        result.put("lockedList",
                proposalRepository.findByStatusAndMangaka_Id("locked", mangaka.getId()));
        return result;
    }

    @Operation(summary = "[SWAGGER] Nộp bản thảo mới")
    @PostMapping(value = "/submit-proposal", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public Map<String, String> handleSubmitting(@RequestParam(required = false) String mangakaId, Model model,
            @RequestParam String txtSeriesName,
            @Parameter(description = "Manuscript file") @RequestPart MultipartFile fileManuscript,
            HttpSession session) {

        Map<String, String> result = new HashMap<>();
        Mangaka currentMangaka = null;

        if (mangakaId != null && !mangakaId.isEmpty()) {
            currentMangaka = mangakaRepository.findById(mangakaId).orElse(null);
        } else {
            User user = (User) session.getAttribute("user");
            if (user != null) {
                currentMangaka = mangakaRepository.findByUser(user).orElse(null);
            }
        }

        if (currentMangaka == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy Mangaka!");
            return result;
        }
        if (fileManuscript.isEmpty()) {
            result.put("status", "error");
            result.put("message", "Vui lòng chọn file bản thảo!");
            return result;
        }

        String fileName = fileManuscript.getOriginalFilename();

        if (fileName == null
                || !fileName.toLowerCase().endsWith(".pdf")) {

            result.put("status", "error");
            result.put("message", "Chỉ được phép tải lên file PDF!");
            return result;
        }

        if (txtSeriesName == null || txtSeriesName.trim().isEmpty()) {
            result.put("status", "error");
            result.put("message", "Vui lòng nhập tên series!");
            return result;
        }

        try {
            String workingDir = System.getProperty("user.dir");
            String uploadDir = workingDir + File.separator + "src" + File.separator + "main" + File.separator
                    + "resources" + File.separator + "static" + File.separator + "proposal" + File.separator;

            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            long currentCount = proposalRepository.count();
            String nextId = String.format("PPS%03d", currentCount + 1);

            String originalName = fileManuscript.getOriginalFilename();
            String extension = ".pdf";
            if (originalName != null && originalName.contains(".")) {
                extension = originalName.substring(originalName.lastIndexOf("."));
            }

            String shortFileName = nextId + extension;
            fileManuscript.transferTo(uploadPath.resolve(shortFileName).toFile());

            Proposal proposal = new Proposal();
            proposal.setId(nextId);
            proposal.setMangaka(currentMangaka);
            proposal.setSeriesName(txtSeriesName.trim());
            proposal.setFilePath("/proposal/" + shortFileName);
            proposal.setStatus("new");
            proposalRepository.save(proposal);

            notificationController.send("tantou", null, "Có đề xuất mới từ Mangaka đang chờ duyệt: " + txtSeriesName,
                    "/manga/editor");

            result.put("status", "success");
            result.put("proposalId", nextId);
            result.put("message", "Đã nộp bản thảo thành công!");

        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        model.addAttribute("activeTab", "tab-proposal");
        return result;
    }

    @PostMapping(value = "/resubmit-proposal", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public Map<String, String> resubmitProposal(@RequestParam("proposalId") String proposalId,
            @RequestParam("txtSeriesName") String txtSeriesName,
            @RequestParam("fileManuscript") MultipartFile fileManuscript) {

        Map<String, String> result = new HashMap<>();

        Proposal proposal = proposalRepository.findById(proposalId).orElse(null);
        if (proposal == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy đề xuất: " + proposalId);
            return result;
        }

        if ("locked".equals(proposal.getStatus())) {
            result.put("status", "error");
            result.put("message", "Đề xuất này không thể nộp lại.");
            return result;
        }

        if (!"revision".equals(proposal.getStatus())) {
            result.put("status", "error");
            result.put("message", "Đề xuất này hiện không ở trạng thái chờ sửa!");
            return result;
        }

        if (fileManuscript.isEmpty()) {
            result.put("status", "error");
            result.put("message", "Vui lòng chọn file bản thảo mới!");
            return result;
        }
        if (txtSeriesName == null || txtSeriesName.trim().isEmpty()) {
            result.put("status", "error");
            result.put("message", "Vui lòng nhập tên series!");
            return result;
        }

        try {
            String workingDir = System.getProperty("user.dir");
            String uploadDir = workingDir + File.separator + "src" + File.separator + "main" + File.separator
                    + "resources" + File.separator + "static" + File.separator + "proposal" + File.separator;

            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Xóa file cũ
            if (proposal.getFilePath() != null) {
                Path oldFile = Paths.get(uploadDir + Paths.get(proposal.getFilePath()).getFileName());
                Files.deleteIfExists(oldFile);
            }

            String originalName = fileManuscript.getOriginalFilename();
            String extension = ".pdf";
            if (originalName != null && originalName.contains(".")) {
                extension = originalName.substring(originalName.lastIndexOf("."));
            }

            String fileName = proposalId + extension;
            fileManuscript.transferTo(uploadPath.resolve(fileName).toFile());

            proposal.setSeriesName(txtSeriesName.trim());
            proposal.setFilePath("/proposal/" + fileName);
            proposal.setStatus("new");
            proposal.setComment(null);
            proposal.setEditorScore(null);
            proposal.setRevisionDeadline(null);
            proposalRepository.save(proposal);

            notificationController.send("tantou", null, "Mangaka đã nộp lại bản thảo: " + txtSeriesName,
                    "/manga/editor");

            result.put("status", "success");
            result.put("proposalId", proposalId);
            result.put("message", "Đã nộp lại bản thảo thành công!");

        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }

    @Operation(summary = "Xem chi tiết đề xuất: comment/điểm/deadline của Tantou + comment của hội đồng")
    @GetMapping("/proposal-detail")
    @ResponseBody
    public Map<String, Object> proposalDetail(@RequestParam String proposalId) {
        Map<String, Object> result = new HashMap<>();
        Proposal p = proposalRepository.findById(proposalId).orElse(null);
        if (p == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy đề xuất: " + proposalId);
            return result;
        }

        result.put("status", "success");
        result.put("proposalId", p.getId());
        result.put("seriesName", p.getSeriesName());
        result.put("proposalStatus", p.getStatus());
        result.put("editorComment", p.getComment());
        result.put("editorScore", p.getEditorScore());
        result.put("revisionDeadline", p.getRevisionDeadline()); // FE tự so sánh với thời gian hiện tại nếu muốn hiển
        // thị "còn X ngày"

        List<Map<String, Object>> boardComments = proposalService
                .getCommentsForProposal(p.getId())
                .stream()
                .map(c -> Map.<String, Object>of(
                        "action", c.getAction(),
                        "content", c.getContent() == null ? "" : c.getContent(),
                        "createdAt", c.getCreatedAt()))
                .toList();
        result.put("boardComments", boardComments);

        return result;
    }


    @Operation(summary = "[SWAGGER] Khởi động series từ proposal đã được duyệt")
    @PostMapping(value = "/start-series", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public Map<String, String> startSeries(@RequestParam String proposalId, @RequestParam String txtSeriesName,
            @RequestParam String txtDescription, @RequestPart MultipartFile fileBookJacket) {

        Map<String, String> result = new HashMap<>();

        Proposal proposal = proposalRepository.findById(proposalId).orElse(null);
        if (proposal == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy đề xuất: " + proposalId);
            return result;
        }

        if (fileBookJacket.isEmpty()) {
            result.put("status", "error");
            result.put("message", "Vui lòng chọn file bìa sách!");
            return result;
        }

        try {
            String uploadDir = System.getProperty("user.dir") + File.separator + "src" + File.separator + "main"
                    + File.separator + "resources" + File.separator + "static" + File.separator + "bookjackets"
                    + File.separator;

            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            long count = seriesRepository.count();
            String seriesId = String.format("SER%03d", count + 1);
            String fileName = seriesId + ".pdf";
            fileBookJacket.transferTo(uploadPath.resolve(fileName).toFile());

            Series series = new Series();
            series.setId(seriesId);
            series.setProposal(proposal);
            series.setSeriesName(txtSeriesName);
            series.setDescription(txtDescription);
            series.setBookJacket("/bookjackets/" + fileName);
            series.setStartDate(LocalDate.now());
            series.setStatus("unfinish");
            seriesRepository.save(series);

            proposal.setStatus("started");
            proposalRepository.save(proposal);

            notificationController.send("tantou", null, "Mangaka đã khởi động dự án mới: " + txtSeriesName,
                    "/manga/editor");

            result.put("status", "success");
            result.put("seriesId", seriesId);
            result.put("message", "Khởi động tác phẩm thành công!");

        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }

    private LocalDate resolveNextSaturday(Series series) {
        Optional<Chapter> latest = chapterRepository.findTopBySeriesOrderByChapterNumberDesc(series);
        if (latest.isPresent() && latest.get().getDeadline() != null) {
            return latest.get().getDeadline().plusWeeks(1);
        }
        return LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
    }

    @Operation(summary = "[SWAGGER] Lấy dữ liệu dashboard của Mangaka")
    @GetMapping("/data")
    @ResponseBody
    public Map<String, Object> getMangakaData(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập");
            return result;
        }
        Mangaka mangaka = mangakaRepository.findByUser(user).orElse(null);
        if (mangaka == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy mangaka");
            return result;
        }
        result.put("status", "success");
        result.put("mangaka", mangaka);
        result.put("mySeriesList", seriesRepository.findByProposal_Mangaka_Id(mangaka.getId()));
        result.put("allProposals", proposalRepository.findByMangaka_Id(mangaka.getId()));
        result.put("approvedList",
                proposalRepository.findByStatusInAndMangaka_Id(List.of("checked", "pass"), mangaka.getId()));
        result.put("rejectedList", proposalRepository.findByStatusAndMangaka_Id("unfinish", mangaka.getId()));
        return result;
    }

    @Operation(summary = "[SWAGGER] Lấy danh sách chapter của một series")
    @GetMapping("/myseries/{seriesId}/data")
    @ResponseBody
    public Map<String, Object> getSeriesData(@PathVariable String seriesId) {
        Map<String, Object> result = new HashMap<>();
        Series series = seriesRepository.findById(seriesId).orElse(null);
        if (series == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy series: " + seriesId);
            return result;
        }
        result.put("status", "success");
        result.put("series", series);
        result.put("chapters", chapterRepository.findBySeries(series));
        return result;
    }

    @Operation(summary = "[SWAGGER] Tạo chapter mới")
    @PostMapping("/myseries/{seriesId}/createchapter/data")
    @ResponseBody
    public Map<String, Object> createChapterData(@PathVariable String seriesId,
            @RequestParam String txtChapterName) {
        Map<String, Object> result = new HashMap<>();
        Series series = seriesRepository.findById(seriesId).orElse(null);
        if (series == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy series: " + seriesId);
            return result;
        }
        try {
            Optional<Chapter> lastChapter = chapterRepository.findTopByOrderByIdDesc();
            int maxId = 0;
            if (lastChapter.isPresent()) {
                maxId = Integer.parseInt(lastChapter.get().getId().substring(3));
            }
            Optional<Chapter> lastChapterNumber = chapterRepository.findTopBySeriesOrderByChapterNumberDesc(series);
            int nextNumber = lastChapterNumber.map(Chapter::getChapterNumber).orElse(0) + 1;
            Chapter chapter = new Chapter();
            chapter.setId("CPT" + String.format("%04d", maxId + 1));
            chapter.setSeries(series);
            chapter.setChapterName(txtChapterName);
            chapter.setChapterNumber(nextNumber);
            chapter.setDeadline(resolveNextSaturday(series));
            chapter.setStatus("unfinish");
            chapterRepository.save(chapter);
            result.put("status", "success");
            result.put("chapterId", chapter.getId());
            result.put("chapterNumber", nextNumber);
            result.put("message", "Tạo chapter thành công!");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }

    @Operation(summary = "[SWAGGER] Lấy danh sách trang của một chapter")
    @GetMapping("/myseries/{sid}/{cid}/data")
    @ResponseBody
    public Map<String, Object> getChapterData(@PathVariable String sid, @PathVariable String cid) {
        Map<String, Object> result = new HashMap<>();
        Chapter chapter = chapterRepository.findById(cid).orElse(null);
        if (chapter == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy chapter: " + cid);
            return result;
        }
        try {
            List<MangaPage> pages = mangaPageRepository.findByChapterId(cid);
            Map<String, Submission> submissionMap = new HashMap<>();
            for (MangaPage page : pages) {
                submissionRepository.findByPageIdId(page.getId())
                        .ifPresent(sub -> submissionMap.put(page.getId(), sub));
            }
            Mangaka mangaka = chapter.getSeries().getProposal().getMangaka();
            result.put("status", "success");
            result.put("chapter", chapter);
            result.put("pages", pages);
            result.put("submissionMap", submissionMap);
            result.put("mangakaId", mangaka.getId());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }

    @Operation(summary = "[SWAGGER] Thêm trang mới vào chapter")
    @PostMapping("/myseries/{seriesId}/{chapterId}/addpage/data")
    @ResponseBody
    public Map<String, Object> addPageData(@PathVariable String seriesId,
            @PathVariable String chapterId,
            @org.springframework.web.bind.annotation.RequestBody(required = false) Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        Chapter chapter = chapterRepository.findById(chapterId).orElse(null);
        if (chapter == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy chapter: " + chapterId);
            return result;
        }

        // ✅ Chapter phải có kịch bản thì mới được tạo trang mới
        if (chapter.getScript() == null || chapter.getScript().trim().isEmpty()) {
            result.put("status", "error");
            result.put("message", "Chapter chưa có kịch bản. Hãy tạo kịch bản trước khi tạo trang mới!");
            return result;
        }

        String pageType = body != null ? body.get("pageType") : null;
        String pageScript = body != null ? body.get("script") : null;

        List<String> allowedTypes = List.of("cover", "action", "rest", "info", "end");
        if (pageType == null || !allowedTypes.contains(pageType)) {
            result.put("status", "error");
            result.put("message", "Vui lòng chọn thể loại trang!");
            return result;
        }
        if (pageScript == null || pageScript.trim().isEmpty()) {
            result.put("status", "error");
            result.put("message", "Vui lòng nhập kịch bản trang!");
            return result;
        }
        if (pageScript.trim().length() > 1000) {
            result.put("status", "error");
            result.put("message", "Kịch bản trang tối đa 1000 chữ!");
            return result;
        }

        try {
            // ✅ Sinh ID dựa trên ID lớn nhất hiện có, không dùng count()
            Optional<MangaPage> lastPage = mangaPageRepository.findTopByOrderByIdDesc();
            int maxId = 0;
            if (lastPage.isPresent()) {
                maxId = Integer.parseInt(lastPage.get().getId().substring(2)); // bỏ tiền tố "PG"
            }
            String pageId = String.format("PG%05d", maxId + 1);

            List<MangaPage> existing = mangaPageRepository.findByChapter(chapter);
            int nextNum = existing.size() + 1;

            MangaPage page = new MangaPage();
            page.setId(pageId);
            page.setChapter(chapter);
            page.setPageNumber(nextNum);
            page.setStatus("unfinish");
            page.setPageType(pageType);
            page.setScript(pageScript.trim());
            mangaPageRepository.save(page);

            result.put("status", "success");
            result.put("pageId", pageId);
            result.put("pageNumber", nextNum);
            result.put("message", "Thêm trang thành công!");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }

    @Operation(summary = "[SWAGGER] Lấy thông tin submission")
    @GetMapping("/submission/{id}/data")
    @ResponseBody
    public Map<String, Object> getSubmissionData(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        Submission submission = submissionRepository.findById(id).orElse(null);
        if (submission == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy submission: " + id);
            return result;
        }
        String seriesId = submission.getPageId().getChapter().getSeries().getId();
        String chapterId = submission.getPageId().getChapter().getId();
        result.put("status", "success");
        result.put("submission", submission);
        result.put("seriesId", seriesId);
        result.put("chapterId", chapterId);
        result.put("returnUrl", "/manga/mangaka/myseries/" + seriesId + "/" + chapterId);
        return result;
    }

    @Operation(summary = "[SWAGGER] Cập nhật trạng thái submission")
    @PostMapping("/submission/{id}/submit/data")
    @ResponseBody
    public Map<String, Object> updateStatusData(@PathVariable String id,
            @RequestParam String status, @RequestParam String comment) {
        Map<String, Object> result = new HashMap<>();
        Submission submission = submissionRepository.findById(id).orElse(null);
        if (submission == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy submission: " + id);
            return result;
        }
        String normalizedStatus = status == null ? "" : status.trim().toLowerCase();
        if (!"pass".equals(normalizedStatus) && !"unfinish".equals(normalizedStatus)) {
            result.put("status", "error");
            result.put("message", "Trạng thái không hợp lệ");
            return result;
        }
        try {
            submission.setStatus(normalizedStatus);
            submission.setComment(comment);
            submissionRepository.save(submission);
            result.put("status", "success");
            result.put("message", "Cập nhật thành công!");
            result.put("seriesId", submission.getPageId().getChapter().getSeries().getId());
            result.put("chapterId", submission.getPageId().getChapter().getId());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }

    // Thêm vào trong MangakaController
    @GetMapping("/{mangakaId}/assistants")
    @ResponseBody
    public List<Assistant> getAssistants(@PathVariable String mangakaId) {
        return assistantRepository.findByMangakaId(mangakaId);
    }

    @Operation(summary = "[SWAGGER] Lấy dữ liệu 1 trang để mở màn vẽ")
    @GetMapping("/myseries/{sid}/{cid}/{pid}/edit/data")
    @ResponseBody
    public Map<String, Object> getPageEditData(@PathVariable String sid, @PathVariable String cid,
            @PathVariable String pid) {
        Map<String, Object> result = new HashMap<>();
        MangaPage page = mangaPageRepository.findById(pid).orElse(null);
        if (page == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy trang: " + pid);
            return result;
        }
        result.put("status", "success");
        result.put("page", page);
        return result;
    }

    @Operation(summary = "[SWAGGER] Lấy danh sách submission intask của các assistant thuộc mangaka")
    @GetMapping("/{mangakaId}/assistant-tasks")
    @ResponseBody
    public Map<String, Object> getAssistantTasks(@PathVariable String mangakaId) {
        Map<String, Object> result = new HashMap<>();
        Mangaka mangaka = mangakaRepository.findById(mangakaId).orElse(null);
        if (mangaka == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy mangaka: " + mangakaId);
            return result;
        }

        List<Submission> submissions = submissionRepository
                .findByAssistant_Mangaka_IdAndStatus(mangakaId, "intask");

        List<Map<String, Object>> tasks = submissions.stream().map(sub -> {
            Map<String, Object> task = new HashMap<>();
            task.put("submissionId", sub.getId());
            task.put("deadline", sub.getDeadline());
            task.put("submissionFilePath", sub.getFilePath());

            // Assistant info
            Assistant assistant = sub.getAssistant();
            task.put("assistantName", assistant != null && assistant.getUser() != null
                    ? assistant.getUser().getFullname()
                    : "Không rõ");

            // Page info
            MangaPage page = sub.getPageId();
            task.put("pageNumber", page != null ? page.getPageNumber() : null);
            task.put("pageFilePath", page != null ? page.getFilePath() : null);

            // Chapter info
            Chapter chapter = page != null ? page.getChapter() : null;
            task.put("chapterNumber", chapter != null ? chapter.getChapterNumber() : null);

            // Series info
            Series series = chapter != null ? chapter.getSeries() : null;
            task.put("seriesName", series != null ? series.getSeriesName() : "Không rõ");

            return task;
        }).toList();

        result.put("status", "success");
        result.put("tasks", tasks);
        return result;
    }
}
