package com.example.manga_management.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    @Operation(summary = "Get chapter details")
    public ResponseEntity<Chapter> getChapter(@PathVariable String chapterId) {

        return chapterRepository.findById(chapterId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{chapterId}")
    @Operation(summary = "Update chapter")
    public ResponseEntity<Chapter> updateChapter(@PathVariable String chapterId, @RequestParam String chapterName) {

        return chapterRepository.findById(chapterId).map(chapter -> {
            chapter.setChapterName(chapterName);
            return ResponseEntity.ok(chapterRepository.save(chapter));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{chapterId}")
    @Operation(summary = "Delete chapter")
    public ResponseEntity<Void> deleteChapter(@PathVariable String chapterId) {

        if (!chapterRepository.existsById(chapterId)) {
            return ResponseEntity.notFound().build();
        }

        chapterRepository.deleteById(chapterId);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{chapterId}/pages")
    @Operation(summary = "Get all pages of a chapter")
    public ResponseEntity<List<MangaPage>> getPagesByChapter(@PathVariable String chapterId) {

        return ResponseEntity.ok(mangaPageRepository.findByChapterId(chapterId));
    }

    @PostMapping("/{chapterId}/pages")
    @Operation(summary = "Create new page")
    public ResponseEntity<MangaPage> createPage(@PathVariable String chapterId, @RequestParam String filePath) {

        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new RuntimeException("Chapter not found"));

        // Sinh PageID
        Optional<MangaPage> lastPage = mangaPageRepository.findTopByOrderByIdDesc();

        int maxId = 0;

        if (lastPage.isPresent()) {
            maxId = Integer.parseInt(lastPage.get().getId().substring(3));
        }

        String newId = "MGP" + String.format("%03d", maxId + 1);

        // Sinh PageNumber
        MangaPage lastChapterPage = mangaPageRepository.findTopByChapterIdOrderByPageNumberDesc(chapterId);

        int nextPageNumber = (lastChapterPage == null) ? 1 : lastChapterPage.getPageNumber() + 1;

        MangaPage page = new MangaPage();
        page.setId(newId);
        page.setChapter(chapter);
        page.setPageNumber(nextPageNumber);
        page.setFilePath(filePath);
        page.setStatus("unfinish");

        return ResponseEntity.ok(mangaPageRepository.save(page));
    }

}