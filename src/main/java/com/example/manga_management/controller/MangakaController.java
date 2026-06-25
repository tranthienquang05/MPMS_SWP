package com.example.manga_management.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.manga_management.entity.Chapter;
import com.example.manga_management.entity.MangaPage;
import com.example.manga_management.entity.Mangaka;
import com.example.manga_management.entity.Proposal;
import com.example.manga_management.entity.Series;
import com.example.manga_management.entity.Submission;
import com.example.manga_management.entity.User;
import com.example.manga_management.repository.ChapterRepository;
import com.example.manga_management.repository.MangaPageRepository;
import com.example.manga_management.repository.MangakaRepository;
import com.example.manga_management.repository.ProposalRepository;
import com.example.manga_management.repository.SeriesRepository;
import com.example.manga_management.repository.SubmissionRepository;

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
    private final ChapterRepository chapterRepository;
    private final MangaPageRepository mangaPageRepository;
    private NotificationController notificationController;

    public MangakaController(ProposalRepository proposalRepository, MangakaRepository mangakaRepository,
            SeriesRepository seriesRepository, ChapterRepository chapterRepository,
            MangaPageRepository mangaPageRepository, SubmissionRepository submissionRepository,
            NotificationController notificationController) {
        this.proposalRepository = proposalRepository;
        this.mangakaRepository = mangakaRepository;
        this.seriesRepository = seriesRepository;
        this.chapterRepository = chapterRepository;
        this.mangaPageRepository = mangaPageRepository;
        this.submissionRepository = submissionRepository;
        this.notificationController = notificationController;
    }

    @Operation(summary = "View the Mangaka dashboard")
    @GetMapping("")
    public String mangakaPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null)
            return "redirect:/login";

        model.addAttribute("user", user);

        Mangaka mangaka = mangakaRepository.findByUser(user).orElse(null);
        model.addAttribute("mangaka", mangaka); // ← move OUTSIDE the if block

        if (mangaka != null) {
            model.addAttribute("mySeriesList",
                    seriesRepository.findByProposal_Mangaka_Id(mangaka.getId()));
            model.addAttribute("allProposals",
                    proposalRepository.findByMangaka_Id(mangaka.getId()));
            model.addAttribute("approvedList",
                    proposalRepository.findByStatusInAndMangaka_Id(
                            List.of("checked", "pass"), mangaka.getId()));
            model.addAttribute("rejectedList",
                    proposalRepository.findByStatusAndMangaka_Id(
                            "unfinish", mangaka.getId()));
        }

        model.addAttribute("activeTab", "tab-home");
        return "mangaka";
    }

    @Operation(summary = "View all proposals for the logged-in Mangaka")
    @GetMapping("/my-projects")
    public String myProjectsPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null)
            return "redirect:/login";

        Mangaka mangaka = mangakaRepository.findByUser(user).orElse(null);
        if (mangaka == null) {
            model.addAttribute("message", "Bạn chưa có thông tin Mangaka!");
            return "mangaka";
        }

        model.addAttribute("mangaka", mangaka);
        model.addAttribute("allProposals",
                proposalRepository.findByMangaka_Id(mangaka.getId()));
        model.addAttribute("approvedList",
                proposalRepository.findByStatusInAndMangaka_Id(
                        List.of("checked", "pass"), mangaka.getId()));
        model.addAttribute("rejectedList",
                proposalRepository.findByStatusAndMangaka_Id(
                        "unfinish", mangaka.getId()));
        model.addAttribute("activeTab", "tab-proposal");
        return "mangaka";
    }

    // ===================== SWAGGER / JSON ENDPOINTS =====================

    @Operation(summary = "[SWAGGER] Xem tất cả proposals của một Mangaka")
    @GetMapping("/my-projects/data")
    @ResponseBody
    public Map<String, Object> myProjectsData(@RequestParam String mangakaId) {
        Map<String, Object> result = new HashMap<>();

        Mangaka mangaka = mangakaRepository.findById(mangakaId).orElse(null);
        if (mangaka == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy Mangaka với ID: " + mangakaId);
            return result;
        }

        result.put("status", "success");
        result.put("allProposals",
                proposalRepository.findByMangaka_Id(mangakaId));
        result.put("approvedList",
                proposalRepository.findByStatusInAndMangaka_Id(
                        List.of("checked", "pass"), mangakaId));
        result.put("rejectedList",
                proposalRepository.findByStatusAndMangaka_Id(
                        "unfinish", mangakaId));
        return result;
    }

    @Operation(summary = "[SWAGGER] Nộp bản thảo mới")
    @PostMapping("/submit-proposal")
    @ResponseBody
    public Map<String, String> handleSubmitting(
            @RequestParam(required = false) String mangakaId,
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

        if (txtSeriesName == null || txtSeriesName.trim().isEmpty()) {
            result.put("status", "error");
            result.put("message", "Vui lòng nhập tên series!");
            return result;
        }

        try {
            String workingDir = System.getProperty("user.dir");
            String uploadDir = workingDir + File.separator + "src" + File.separator + "main"
                    + File.separator + "resources" + File.separator + "static"
                    + File.separator + "proposal" + File.separator;

            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath))
                Files.createDirectories(uploadPath);

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
            proposal.setStatus("finish");
            proposalRepository.save(proposal);

            notificationController.send("tantou", null,
                    "Có đề xuất mới từ Mangaka đang chờ duyệt: " + txtSeriesName,
                    "/manga/editor");

            result.put("status", "success");
            result.put("proposalId", nextId);
            result.put("message", "Đã nộp bản thảo thành công!");

        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }

    @Operation(summary = "[SWAGGER] Nộp lại bản thảo bị từ chối")
    @PostMapping("/resubmit-proposal")
    @ResponseBody
    public Map<String, String> resubmitProposal(
            @RequestParam String proposalId,
            @RequestParam String txtSeriesName,
            @RequestPart MultipartFile fileManuscript) {

        Map<String, String> result = new HashMap<>();

        Proposal proposal = proposalRepository.findById(proposalId).orElse(null);
        if (proposal == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy đề xuất: " + proposalId);
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
            String uploadDir = workingDir + File.separator + "src" + File.separator + "main"
                    + File.separator + "resources" + File.separator + "static"
                    + File.separator + "proposal" + File.separator;

            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath))
                Files.createDirectories(uploadPath);

            // Xóa file cũ
            if (proposal.getFilePath() != null) {
                Path oldFile = Paths.get(uploadDir
                        + Paths.get(proposal.getFilePath()).getFileName());
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
            proposal.setStatus("finish");
            proposal.setComment(null);
            proposalRepository.save(proposal);

            notificationController.send("tantou", null,
                    "Mangaka đã nộp lại bản thảo: " + txtSeriesName,
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

    @Operation(summary = "[SWAGGER] Khởi động series từ proposal đã được duyệt")
    @PostMapping("/start-series")
    @ResponseBody
    public Map<String, String> startSeries(
            @RequestParam String proposalId,
            @RequestParam String txtSeriesName,
            @RequestParam String txtDescription,
            @RequestPart MultipartFile fileBookJacket) {

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
            String uploadDir = System.getProperty("user.dir") + File.separator + "src"
                    + File.separator + "main" + File.separator + "resources"
                    + File.separator + "static" + File.separator + "bookjackets"
                    + File.separator;

            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath))
                Files.createDirectories(uploadPath);

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

            notificationController.send("tantou", null,
                    "Mangaka đã khởi động dự án mới: " + txtSeriesName,
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

    @Operation(summary = "View series details and chapters", description = "Allows a Mangaka to view the details of a specific series along with its chapters. Requires the series ID.")
    @GetMapping("/myseries/{seriesId}")
    public String viewSeries(@PathVariable String seriesId, HttpSession session, Model model) {

        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        Series series = seriesRepository.findById(seriesId).orElse(null);

        if (series == null) {
            model.addAttribute("message", "Series không tồn tại!");
            return "redirect:/manga/mangaka/myseries";
        }

        model.addAttribute("series", series);
        model.addAttribute("chapters", chapterRepository.findBySeries(series));
        model.addAttribute("activeTab", "tab-project");

        return "mangaka";
    }

    @PostMapping("/myseries/{seriesId}/createchapter")
    public String createChapter(@PathVariable String seriesId, @RequestParam String txtChapterName, HttpSession session,
            Model model, RedirectAttributes redirectAttributes) {
        Series series = seriesRepository.findById(seriesId).orElse(null);

        if (series == null) {
            model.addAttribute("message", "Series không tồn tại!");
            return "redirect:/manga/mangaka/myseries";
        }
        Optional<Chapter> lastChapter = chapterRepository.findTopByOrderByIdDesc();

        int maxId = 0;

        if (lastChapter.isPresent()) {
            maxId = Integer.parseInt(lastChapter.get().getId().substring(3));
        }
        Optional<Chapter> lastChapternumber = chapterRepository.findTopBySeriesOrderByChapterNumberDesc(series);

        int nextNumber = lastChapternumber.map(Chapter::getChapterNumber).orElse(0) + 1;

        // Tạo chapter mới
        Chapter chapter = new Chapter();
        chapter.setId("CPT" + String.format("%04d", maxId + 1));
        chapter.setSeries(series);
        chapter.setChapterName(txtChapterName);
        chapter.setChapterNumber(nextNumber);
        chapter.setStatus("unfinish"); // Mặc định khi tạo mới

        chapterRepository.save(chapter);

        model.addAttribute("message", "Tạo Chapter thành công!");

        return "mangaka";
    }

    @GetMapping("/myseries/{sid}/{cid}")
    public String viewChapter(@PathVariable String sid, @PathVariable String cid, Model model) {

        Chapter chapter = chapterRepository.findById(cid).orElseThrow();

        List<MangaPage> pages = mangaPageRepository.findByChapterId(cid);

        Map<String, Submission> submissionMap = new HashMap<>();

        for (MangaPage page : pages) {
            submissionRepository.findByPageIdId(page.getId()).ifPresent(sub -> submissionMap.put(page.getId(), sub));
        }
        Mangaka mangaka = chapter.getSeries().getProposal().getMangaka();

        model.addAttribute("chapter", chapter);
        model.addAttribute("pages", pages);
        model.addAttribute("submissionMap", submissionMap);
        model.addAttribute("activeTab", "tab-project");
        model.addAttribute("mangakaId", mangaka.getId());
        return "mangaka";
    }

    @GetMapping("/myseries/{seriesId}/{chapterId}/{pageId}/edit")
    public String editPage(@PathVariable String seriesId, @PathVariable String chapterId, @PathVariable String pageId,
            Model model, HttpSession session) {

        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        MangaPage page = mangaPageRepository.findById(pageId).orElse(null);
        if (page == null) {
            model.addAttribute("message", "Trang không tồn tại!");
            // Đã sửa redirect cho đúng với cấu trúc route hiển thị của hệ thống
            return "mangaka";
        }

        model.addAttribute("page", page);
        model.addAttribute("activeTab", "tab-draw");
        return "mangaka";
    }

    // Tạo trang mới
    @PostMapping("/myseries/{seriesId}/{chapterId}/addpage")
    public String addPage(@PathVariable String seriesId, @PathVariable String chapterId) {
        Chapter chapter = chapterRepository.findById(chapterId).orElse(null);
        if (chapter == null) {
            return "redirect:/manga/mangaka/myseries/" + seriesId;
        }

        long count = mangaPageRepository.count();
        String pageId = String.format("PG%05d", count + 1);

        List<MangaPage> existing = mangaPageRepository.findByChapter(chapter);
        int nextNum = existing.size() + 1;

        MangaPage page = new MangaPage();
        page.setId(pageId);
        page.setChapter(chapter);
        page.setPageNumber(nextNum);
        page.setStatus("unfinish");
        mangaPageRepository.save(page);

        return "redirect:/manga/mangaka/myseries/" + seriesId + "/" + chapterId + "/" + pageId + "/edit";
    }

}
