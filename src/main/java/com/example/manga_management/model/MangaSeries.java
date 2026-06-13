package com.example.manga_management.model;

public class MangaSeries {
    private Long id;
    private String title;
    private String description;
    private Long mangakaId; // ID của họa sĩ tạo truyện
    private String status; // DRAFT, PENDING_EDITOR, VOTING, APPROVED, REJECTED

    public MangaSeries() {
    }

    public MangaSeries(Long id, String title, String description, Long mangakaId, String status) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.mangakaId = mangakaId;
        this.status = status;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getMangakaId() {
        return mangakaId;
    }

    public void setMangakaId(Long mangakaId) {
        this.mangakaId = mangakaId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}