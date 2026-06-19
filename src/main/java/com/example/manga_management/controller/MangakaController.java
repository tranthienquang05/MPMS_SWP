package com.example.manga_management.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.manga_management.entity.Chapter;
import com.example.manga_management.entity.Mangaka;
import com.example.manga_management.entity.Proposal;
import com.example.manga_management.entity.Series;
import com.example.manga_management.entity.User;
import com.example.manga_management.repository.ChapterRepository;
import com.example.manga_management.repository.MangakaRepository;
import com.example.manga_management.repository.SeriesRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.example.manga_management.repository.MangakaRepository;
import com.example.manga_management.repository.ProposalRepository;
import com.example.manga_management.repository.SeriesRepository;
import jakarta.servlet.http.HttpSession;

@Tag(name = "Mangaka Controller", description = "Endpoints for Mangaka operations, including project submission, series management, and chapter creation.")
@Controller
@RequestMapping("/manga/mangaka")
public class MangakaController {

    private final ProposalRepository proposalRepository;
    private final MangakaRepository mangakaRepository;
    private final SeriesRepository seriesRepository;
    private final ChapterRepository chapterRepository;

    public MangakaController(ProposalRepository proposalRepository, MangakaRepository mangakaRepository, SeriesRepository seriesRepository, ChapterRepository chapterRepository) {
        this.proposalRepository = proposalRepository;
        this.mangakaRepository = mangakaRepository;
        this.seriesRepository = seriesRepository;
        this.chapterRepository = chapterRepository;
    }

    @GetMapping("")
    public String mangakaPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null)
            return "redirect:/login";

        model.addAttribute("user", user);
        return "mangaka";
    }

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

        List<Proposal> projectList = proposalRepository.findByStatusAndMangaka_Id("pass", mangaka.getId());

        model.addAttribute("projectList", projectList);

        return "mangaka";
    }

    @PostMapping("/submit-proposal")
    public String handleSubmitting(
            @RequestParam String txtSeriesName,
            @RequestParam MultipartFile fileManuscript,
            HttpSession session,
            Model model) {

        User user = (User) session.getAttribute("user");
        if (user == null)
            return "redirect:/login";

        Mangaka currentMangaka = mangakaRepository.findByUser(user).orElse(null);
        if (currentMangaka == null) {
            model.addAttribute("message", "Lỗi: Tài khoản không có vai trò Mangaka phù hợp!");
            return "mangaka";
        }

        if (fileManuscript.isEmpty()) {
            model.addAttribute("message", "Vui lòng chọn một file bản thảo để nộp!");
            return "mangaka";
        }

        if (txtSeriesName == null || txtSeriesName.trim().isEmpty()) {
            model.addAttribute("message", "Vui lòng nhập tên series!");
            return "mangaka";
        }

        try {
            // Get the correct upload directory path based on working directory
            String workingDir = System.getProperty("user.dir");
            String uploadDir = workingDir + File.separator + "src" + File.separator + "main" + File.separator
                    + "resources" + File.separator + "static" + File.separator + "proposal" + File.separator;

            Path uploadPath = Paths.get(uploadDir);

            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Tính toán ID trước để lấy tên đặt cho file
            long currentCount = proposalRepository.count();
            String nextId = String.format("PPS%03d", currentCount + 1);

            // Đục đuôi file gốc (.pdf, .zip...)
            String originalName = fileManuscript.getOriginalFilename();
            String extension = ".pdf";
            if (originalName != null && originalName.contains(".")) {
                extension = originalName.substring(originalName.lastIndexOf("."));
            }

            // Đặt tên file theo ID để tối ưu độ dài chuỗi
            String shortFileName = nextId + extension;
            Path destinationPath = uploadPath.resolve(shortFileName);
            fileManuscript.transferTo(destinationPath.toFile());

            Proposal proposal = new Proposal();
            proposal.setId(nextId);
            proposal.setMangaka(currentMangaka);
            proposal.setSeriesName(txtSeriesName.trim());
            proposal.setFilePath("/proposal/" + shortFileName);
            proposal.setStatus("finish");

            proposalRepository.save(proposal);

            model.addAttribute("message", "Đã nộp dự án mới và lưu bản thảo thành công!");
        } catch (IOException e) {
            e.printStackTrace();
            model.addAttribute("message", "Lỗi hệ thống khi lưu file: " + e.getMessage());
        }

        model.addAttribute("user", user);
        model.addAttribute("message", "Đã nộp dự án mới thành công!");
        model.addAttribute("activeTab", "tab-project");
        return "mangaka";
    }

    @PostMapping("/start-series")
    public String startSeries(
            @RequestParam String proposalId,
            @RequestParam String txtSeriesName,
            @RequestParam String txtDescription,
            @RequestParam MultipartFile fileBookJacket,
            HttpSession session, Model model) {

        // 1. Kiểm tra proposal
        Proposal proposal = proposalRepository.findById(proposalId).orElse(null);
        if (proposal == null) {
            model.addAttribute("message", "Lỗi: Không tìm thấy đề xuất!");
            return "mangaka";
        }

        // 2. Xử lý lưu file
        if (fileBookJacket.isEmpty()) {
            model.addAttribute("message", "Vui lòng chọn file bìa sách (PDF)!");
            return "mangaka";
        }

        try {
            String uploadDir = System.getProperty("user.dir") + File.separator + "src" + File.separator + "main"
                    + File.separator + "resources" + File.separator + "static" + File.separator + "bookjackets"
                    + File.separator;

            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath))
                Files.createDirectories(uploadPath);

            // Tạo ID mới cho Series
            long count = seriesRepository.count();
            String seriesId = String.format("SER%03d", count + 1);
            String fileName = seriesId + ".pdf"; // Lưu cứng đuôi .pdf

            fileBookJacket.transferTo(uploadPath.resolve(fileName).toFile());

            // 3. Tạo Series
            Series series = new Series();
            series.setId(seriesId);
            series.setProposal(proposal);
            series.setSeriesName(txtSeriesName);
            series.setDescription(txtDescription);
            series.setBookJacket("/bookjackets/" + fileName);
            series.setStartDate(LocalDate.now());
            series.setStatus("unfinish"); // Mặc định khi bắt đầu

            seriesRepository.save(series);

            // 4. Update Proposal để không bị trùng lặp
            proposal.setStatus("unfinish"); 
            proposalRepository.save(proposal);

            model.addAttribute("message", "Khởi động tác phẩm thành công!");
        } catch (IOException e) {
            model.addAttribute("message", "Lỗi hệ thống: " + e.getMessage());
        }
        model.addAttribute("message", "Khởi động tác phẩm thành công!");
        model.addAttribute("activeTab", "tab-project");
        return "mangaka";

    }


    @GetMapping("/myseries/{seriesId}")
    public String viewSeries(
            @PathVariable String seriesId,
            HttpSession session,
            Model model) {

        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        Series series = seriesRepository
                .findById(seriesId)
                .orElse(null);

        if (series == null) {
            model.addAttribute("message", "Series không tồn tại!");
            return "redirect:/manga/mangaka/myseries";
        }

        model.addAttribute("series", series);
        model.addAttribute(
                "chapters",
                chapterRepository.findBySeries(series));

        return "manga/mangaka/viewseries";
    }
    @Operation(summary = "Create a new chapter for a specific series", description = "Allows a Mangaka to create a new chapter for their series. Requires the series ID and chapter details.")
    @GetMapping("/myseries/{seriesId}/createchapter")
    public String createChapterPage(
            @PathVariable String seriesId,
            HttpSession session,
            Model model) {

        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        Series series = seriesRepository
                .findById(seriesId)
                .orElse(null);

        if (series == null) {
            return "redirect:/manga/mangaka/myseries";
        }

        model.addAttribute("series", series);
        model.addAttribute("chapter", new Chapter());

        return "manga/mangaka/createchapter";
    }

    @PostMapping("/myseries/{seriesId}/createchapter")
    public String createChapter(
            @PathVariable String seriesId,
            @ModelAttribute Chapter chapter,
            RedirectAttributes redirectAttributes) {

        Series series = seriesRepository
                .findById(seriesId)
                .orElse(null);

        if (series == null) {
            redirectAttributes.addFlashAttribute(
                    "message",
                    "Series không tồn tại!");
            return "redirect:/manga/mangaka/myseries";
        }

        String chapterId = "CPT"
                + String.format("%04d",
                        chapterRepository.count() + 1);

        chapter.setId(chapterId);
        chapter.setSeries(series);

        if (chapter.getStatus() == null
                || chapter.getStatus().isBlank()) {
            chapter.setStatus("unfinish");
        }

        chapterRepository.save(chapter);

        redirectAttributes.addFlashAttribute(
                "message",
                "Tạo Chapter thành công!");

        return "redirect:/manga/mangaka/myseries/" + seriesId;
    }

}