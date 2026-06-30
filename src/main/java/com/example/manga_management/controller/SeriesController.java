package com.example.manga_management.controller;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;

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
import com.example.manga_management.entity.Series;
import com.example.manga_management.repository.ChapterRepository;
import com.example.manga_management.repository.SeriesRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/series")
@Tag(name = "Series", description = "Series Management APIs")
public class SeriesController {

    @Autowired
    private SeriesRepository seriesRepository;
    @Autowired
    private ChapterRepository chapterRepository;

    // Get all series
    @GetMapping
    @Operation(summary = "Get all series")
    public List<Series> getAllSeries() {
        return seriesRepository.findAll();
    }

    // Lấy danh sách series theo Mangaka ID
    @GetMapping("/mangaka/{mangakaId}")
    @Operation(summary = "Get all series by Mangaka ID")
    public ResponseEntity<List<Series>> getSeriesByMangakaId(@PathVariable String mangakaId) {

        List<Series> seriesList = seriesRepository.findByProposal_Mangaka_Id(mangakaId);

        if (seriesList.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(seriesList);
    }

    // Get series by ID
    @GetMapping("/{seriesId}")
    @Operation(summary = "Get series details")
    public ResponseEntity<Series> getSeriesById(@PathVariable String seriesId) {

        return seriesRepository.findById(seriesId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    // Create series
    @PostMapping("/create")
    @Operation(summary = "Create new series")
    public ResponseEntity<Series> createSeries(@RequestBody Series series) {

        Series saved = seriesRepository.save(series);
        return ResponseEntity.ok(saved);
    }

    // Update series
    @PutMapping("/{seriesId}")
    @Operation(summary = "Update series")
    public ResponseEntity<Series> updateSeries(@PathVariable String seriesId, @RequestBody Series updatedSeries) {

        return seriesRepository.findById(seriesId).map(series -> {
            series.setSeriesName(updatedSeries.getSeriesName());
            series.setDescription(updatedSeries.getDescription());

            return ResponseEntity.ok(seriesRepository.save(series));
        }).orElse(ResponseEntity.notFound().build());
    }

    // Delete series
    @DeleteMapping("/{seriesId}")
    @Operation(summary = "Delete series")
    public ResponseEntity<Void> deleteSeries(@PathVariable String seriesId) {

        if (!seriesRepository.existsById(seriesId)) {
            return ResponseEntity.notFound().build();
        }

        seriesRepository.deleteById(seriesId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{seriesId}/chapters")
    @Operation(summary = "Get all chapters of a series")
    public ResponseEntity<List<Chapter>> getChaptersBySeriesId(@PathVariable String seriesId) {

        List<Chapter> chapters = chapterRepository.findBySeriesId(seriesId);

        return ResponseEntity.ok(chapters);
    }

    @PostMapping("/{seriesId}/chapters")
    @Operation(summary = "Create new chapter")
    public ResponseEntity<Chapter> createChapter(@PathVariable String seriesId, @RequestParam String chapterName) {

        Series series = seriesRepository.findById(seriesId).orElseThrow(() -> new RuntimeException("Series not found"));

        // Sinh ID
        Optional<Chapter> lastChapter = chapterRepository.findTopByOrderByIdDesc();

        int maxId = 0;

        if (lastChapter.isPresent()) {
            maxId = Integer.parseInt(lastChapter.get().getId().substring(3));
        }

        String newId = "CHP" + String.format("%03d", maxId + 1);

        // Tính chapterNumber
        List<Chapter> chapters = chapterRepository.findBySeriesId(seriesId);

        int nextChapterNumber = chapters.size() + 1;

        Chapter chapter = new Chapter();
        chapter.setId(newId);
        chapter.setSeries(series);
        chapter.setChapterName(chapterName);
        chapter.setChapterNumber(nextChapterNumber);
        chapter.setDeadline(resolveNextSaturday(series));
        chapter.setStatus("unfinish");

        Chapter saved = chapterRepository.save(chapter);

        return ResponseEntity.ok(saved);
    }

    private LocalDate resolveNextSaturday(Series series) {
        Optional<Chapter> latest = chapterRepository.findTopBySeriesOrderByChapterNumberDesc(series);
        if (latest.isPresent() && latest.get().getDeadline() != null) {
            return latest.get().getDeadline().plusWeeks(1);
        }
        return LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
    }
}
