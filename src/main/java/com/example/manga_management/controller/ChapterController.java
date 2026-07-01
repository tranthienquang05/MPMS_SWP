package com.example.manga_management.controller;

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
import com.example.manga_management.entity.MangaPage;
import com.example.manga_management.repository.ChapterRepository;
import com.example.manga_management.repository.MangaPageRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/chapters")
@Tag(name = "Chapter", description = "Chapter Management APIs")
public class ChapterController {

    @Autowired
    private ChapterRepository chapterRepository;
    @Autowired
    private MangaPageRepository mangaPageRepository;

    @GetMapping("/{chapterId}")
    @Operation(summary = "[SWAGGER] Lấy thông tin chi tiết một chapter")
    @ResponseBody
    public Map<String, Object> getChapter(@PathVariable String chapterId) {
        Map<String, Object> result = new HashMap<>();

        Chapter chapter = chapterRepository.findById(chapterId).orElse(null);
        if (chapter == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy chapter: " + chapterId);
            return result;
        }

        result.put("status", "success");
        result.put("chapter", chapter);
        return result;
    }

    @PutMapping("/{chapterId}")
    @Operation(summary = "[SWAGGER] Cập nhật tên chapter")
    @ResponseBody
    public Map<String, Object> updateChapter(@PathVariable String chapterId, @RequestParam String chapterName) {
        Map<String, Object> result = new HashMap<>();

        Chapter chapter = chapterRepository.findById(chapterId).orElse(null);
        if (chapter == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy chapter: " + chapterId);
            return result;
        }

        chapter.setChapterName(chapterName);
        chapterRepository.save(chapter);

        result.put("status", "success");
        result.put("chapterId", chapterId);
        result.put("message", "Cập nhật chapter thành công!");
        return result;
    }

    @DeleteMapping("/{chapterId}")
    @Operation(summary = "[SWAGGER] Xóa một chapter")
    @ResponseBody
    public Map<String, String> deleteChapter(@PathVariable String chapterId) {
        Map<String, String> result = new HashMap<>();

        if (!chapterRepository.existsById(chapterId)) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy chapter: " + chapterId);
            return result;
        }

        chapterRepository.deleteById(chapterId);

        result.put("status", "success");
        result.put("message", "Đã xóa chapter: " + chapterId);
        return result;
    }

    @GetMapping("/{chapterId}/pages")
    @Operation(summary = "[SWAGGER] Lấy danh sách trang của một chapter")
    @ResponseBody
    public Map<String, Object> getPagesByChapter(@PathVariable String chapterId) {
        Map<String, Object> result = new HashMap<>();

        Chapter chapter = chapterRepository.findById(chapterId).orElse(null);
        if (chapter == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy chapter: " + chapterId);
            return result;
        }

        List<MangaPage> pages = mangaPageRepository.findByChapterId(chapterId);
        result.put("status", "success");
        result.put("chapterId", chapterId);
        result.put("pages", pages);
        return result;
    }

    @PostMapping("/{chapterId}/pages")
    @Operation(summary = "[SWAGGER] Tạo trang mới trong một chapter")
    @ResponseBody
    public Map<String, Object> createPage(@PathVariable String chapterId, @RequestParam String filePath) {
        Map<String, Object> result = new HashMap<>();

        Chapter chapter = chapterRepository.findById(chapterId).orElse(null);
        if (chapter == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy chapter: " + chapterId);
            return result;
        }

        try {
            Optional<MangaPage> lastPage = mangaPageRepository.findTopByOrderByIdDesc();
            int maxId = 0;
            if (lastPage.isPresent()) {
                maxId = Integer.parseInt(lastPage.get().getId().substring(3));
            }
            String newId = "MGP" + String.format("%03d", maxId + 1);

            MangaPage lastChapterPage = mangaPageRepository.findTopByChapterIdOrderByPageNumberDesc(chapterId);
            int nextPageNumber = (lastChapterPage == null) ? 1 : lastChapterPage.getPageNumber() + 1;

            MangaPage page = new MangaPage();
            page.setId(newId);
            page.setChapter(chapter);
            page.setPageNumber(nextPageNumber);
            page.setFilePath(filePath);
            page.setStatus("unfinish");

            mangaPageRepository.save(page);

            result.put("status", "success");
            result.put("pageId", newId);
            result.put("pageNumber", nextPageNumber);
            result.put("message", "Tạo trang mới thành công!");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }

        return result;
    }
}
