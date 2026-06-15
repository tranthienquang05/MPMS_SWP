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
            File destinationFile = new File(UPLOAD_DIR + shortFileName);
            fileManuscript.transferTo(destinationFile);

            Proposal proposal = Proposal.builder()
                    .id(nextId)
                    .mangaka(currentMangaka)
                    .seriesName(txtSeriesName)                                       
                    .filePath("/uploads/" + shortFileName) 
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