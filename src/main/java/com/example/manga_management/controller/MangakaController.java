package com.example.manga_management.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.manga_management.entity.Mangaka;
import com.example.manga_management.entity.Proposal;
import com.example.manga_management.entity.Series;
import com.example.manga_management.entity.User;
import com.example.manga_management.repository.SeriesRepository;
import com.example.manga_management.repository.MangakaRepository;
import com.example.manga_management.repository.ProposalRepository;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/manga/mangaka")
public class MangakaController {

    private final ProposalRepository proposalRepository;
    private final MangakaRepository mangakaRepository;
    private final SeriesRepository seriesRepository;

    public MangakaController(ProposalRepository proposalRepository, MangakaRepository mangakaRepository, SeriesRepository seriesRepository) {
        this.proposalRepository = proposalRepository;
        this.mangakaRepository = mangakaRepository;
        this.seriesRepository = seriesRepository;
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
        return "mangaka";

    }

    @PostMapping("/createseries")
    public String createSeries(
        @ModelAttribute Series series,
        @RequestParam("proposalId") String proposalId,
        HttpSession session,
        RedirectAttributes redirectAttributes) {

    User user = (User) session.getAttribute("user");
    if (user == null) {
        return "redirect:/login";
    }

    Proposal proposal = proposalRepository
            .findById(proposalId)
            .orElse(null);

    if (proposal == null) {
        redirectAttributes.addFlashAttribute(
                "message",
                "Proposal không tồn tại!");
        return "redirect:/manga/mangaka/createseries";
    }

    // Tạo mã Series
    String seriesId = "SER" + String.format("%03d",seriesRepository.count() + 1);

    series.setId(seriesId);
    series.setProposal(proposal);

    if (series.getStatus() == null) {
        series.setStatus("unfinish");
    }

    seriesRepository.save(series);

    redirectAttributes.addFlashAttribute(
            "message",
            "Tạo Series thành công!");

    return "redirect:/manga/mangaka/myseries";
    }

}