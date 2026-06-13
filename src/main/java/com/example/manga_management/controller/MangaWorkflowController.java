package com.example.manga_management.controller;

import com.example.manga_management.model.MangaSeries;
import com.example.manga_management.model.Chapter;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/manga")
public class MangaWorkflowController {

    // Tạo danh sách tĩnh để lưu dữ liệu tạm thời trong bộ nhớ
    private static List<MangaSeries> seriesList = new ArrayList<>();
    private static List<Chapter> chapterList = new ArrayList<>();
    private static long seriesIdCounter = 1;
    private static long chapterIdCounter = 1;

    // Khởi tạo dữ liệu mẫu ban đầu
    static {
        seriesList.add(new MangaSeries(seriesIdCounter++, "Đảo Hải Tặc", "Hành trình tìm kho báu", 101L, "DRAFT"));
        seriesList.add(new MangaSeries(seriesIdCounter++, "Đại Chiến Titan", "Cuộc chiến sinh tồn", 102L, "APPROVED"));

        chapterList.add(new Chapter(chapterIdCounter++, 2L, "Chapter 1: To You, 2000 Years From Now", 1, "PUBLISHED"));
    }

    // 1. MÀN HÌNH CHÍNH: Hiển thị danh sách dựa theo sơ đồ quy trình
    @GetMapping("/dashboard")
    public String showDashboard(Model model) {
        model.addAttribute("allSeries", seriesList);
        model.addAttribute("allChapters", chapterList);
        return "dashboard"; // Sẽ tìm file dashboard.html trong thư mục templates
    }

    // 2. LUỒNG MANGAKA: Tạo bản thảo Series mới (Create Series)
    @PostMapping("/create-series")
    public String createSeries(@RequestParam String title, @RequestParam String description) {
        // Mặc định tạo mới sẽ có trạng thái là DRAFT (Bản thảo) theo đúng sơ đồ
        MangaSeries newSeries = new MangaSeries(seriesIdCounter++, title, description, 101L, "DRAFT");
        seriesList.add(newSeries);
        return "redirect:/manga/dashboard"; // Quay lại trang chủ sau khi thêm
    }

    // 3. LUỒNG EDITOR: Duyệt bản thảo để đưa lên Hội đồng bỏ phiếu (Review draft)
    @PostMapping("/review-draft/{id}")
    public String reviewDraft(@PathVariable Long id, @RequestParam String action) {
        for (MangaSeries series : seriesList) {
            if (series.getId().equals(id)) {
                if ("approve".equals(action)) {
                    series.setStatus("VOTING"); // Chuyển sang trạng thái chờ Hội đồng Vote
                } else {
                    series.setStatus("REJECTED"); // Từ chối (Notification of rejection)
                }
                break;
            }
        }
        return "redirect:/manga/dashboard";
    }

    // 4. LUỒNG EDITOR BOARD: Bỏ phiếu quyết định xuất bản (Vote -> Start the
    // Series)
    @PostMapping("/vote-series/{id}")
    public String voteSeries(@PathVariable Long id, @RequestParam boolean isFinalApproved) {
        for (MangaSeries series : seriesList) {
            if (series.getId().equals(id)) {
                if (isFinalApproved) {
                    series.setStatus("APPROVED"); // Chính thức thông qua (Start the Series)
                } else {
                    series.setStatus("REJECTED");
                }
                break;
            }
        }
        return "redirect:/manga/dashboard";
    }
}