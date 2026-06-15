package com.example.manga_management.controller;

import com.example.manga_management.entity.Mangaka;
import com.example.manga_management.entity.Proposal;
import com.example.manga_management.entity.User;
import com.example.manga_management.repository.MangakaRepository;
import com.example.manga_management.repository.ProposalRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Controller
@RequestMapping("/manga/mangaka")
public class MangakaController {

    private final ProposalRepository proposalRepository;
    private final MangakaRepository mangakaRepository;
    private final String UPLOAD_DIR = "D:/LEARNING/SWP391/uploads/";

    public MangakaController(ProposalRepository proposalRepository, MangakaRepository mangakaRepository) {
        this.proposalRepository = proposalRepository;
        this.mangakaRepository = mangakaRepository;
    }

    // THÊM MỚI: Đứng ra chịu trách nhiệm hiển thị trang làm việc của Mangaka, vá lỗi tranh chấp route
    @GetMapping
    public String mangakaPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/manga/login";
        
        model.addAttribute("user", user);
        return "mangaka";
    }

    @PostMapping("/submit-proposal")
    public String handleSubmitting(
            @RequestParam String txtSeriesName,
            @RequestParam String txtContent,
            @RequestParam MultipartFile fileManuscript,
            HttpSession session, 
            Model model) {

        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/manga/login";

        Mangaka currentMangaka = mangakaRepository.findByUser(user).orElse(null);
        if (currentMangaka == null) {
            model.addAttribute("error", "Lỗi: Tài khoản không có vai trò Mangaka phù hợp!");
            return "mangaka";
        }

        if (fileManuscript.isEmpty()) {
            model.addAttribute("error", "Vui lòng chọn một file bản thảo để nộp!");
            return "mangaka";
        }

        try {
            File folder = new File(UPLOAD_DIR);
            if (!folder.exists()) {
                folder.mkdirs();
            }

            String uniqueFileName = UUID.randomUUID().toString() + "_" + fileManuscript.getOriginalFilename();
            File destinationFile = new File(UPLOAD_DIR + uniqueFileName);
            fileManuscript.transferTo(destinationFile);

            long currentCount = proposalRepository.count();
            String nextId = String.format("PPS%03d", currentCount + 1);

            Proposal proposal = Proposal.builder()
                    .id(nextId)
                    .mangaka(currentMangaka)
                    .seriesName(txtSeriesName)
                    .content(txtContent)
                    .filePath("/uploads/" + uniqueFileName)
                    .status("pending")
                    .build();

            proposalRepository.save(proposal);
            
            model.addAttribute("message", "Đã nộp dự án mới và lưu bản thảo thành công!");
        } catch (IOException e) {
            e.printStackTrace();
            model.addAttribute("error", "Lỗi hệ thống khi lưu file: " + e.getMessage());
        }

        model.addAttribute("user", user);
        return "mangaka";
    }
}