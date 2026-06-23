package com.example.manga_management.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.manga_management.entity.MangaPage;
import com.example.manga_management.repository.MangaPageRepository;

@RestController
@RequestMapping("/api/page")
public class PageController {
    @Autowired
    private final MangaPageRepository mangaPageRepository;

    public PageController(MangaPageRepository mangaPageRepository) {
        this.mangaPageRepository = mangaPageRepository;
    }

    @PostMapping("/{pageId}/savefile")
    @ResponseBody
    public Map<String, String> savePageFile(@PathVariable String pageId, @RequestBody Map<String, String> body) {

        try {
            MangaPage page = mangaPageRepository.findById(pageId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy page: " + pageId));

            String base64 = body.get("imageBase64");
            if (base64 != null && base64.contains(",")) {
                base64 = base64.split(",")[1];
            }
            if (base64 == null || base64.isBlank()) {
                return Map.of("status", "error", "message", "imageBase64 trống");
            }

            byte[] imageBytes = Base64.getDecoder().decode(base64);

            String uploadDir = "src/main/resources/static/MangaPage/";
            Files.createDirectories(Paths.get(uploadDir));

            String fileName = pageId + ".png";
            Path filePath = Paths.get(uploadDir + fileName);
            Files.write(filePath, imageBytes);

            page.setFilePath("/MangaPage/" + fileName);
            page.setStatus("unfinish");
            mangaPageRepository.save(page);

            String chapterId = page.getChapter().getId();
            String seriesId = page.getChapter().getSeries().getId();

            return Map.of("status", "success", "redirectUrl", "/manga/mangaka/myseries/" + seriesId + "/" + chapterId);

        } catch (IllegalArgumentException e) {
            return Map.of("status", "error", "message", "Base64 không hợp lệ");
        } catch (IOException e) {
            return Map.of("status", "error", "message", "Lỗi ghi file: " + e.getMessage());
        } catch (Exception e) {
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

}