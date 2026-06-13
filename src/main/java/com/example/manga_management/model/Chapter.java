package com.example.manga_management.model;

public class Chapter {
    private Long id;
    private Long seriesId; // Thuộc bộ truyện nào
    private String title;
    private int volumeNumber;
    private String status; // SKETCHING, REVIEWING, AMENDMENT_REQUIRED, PUBLISHED

    public Chapter() {
    }

    public Chapter(Long id, Long seriesId, String title, int volumeNumber, String status) {
        this.id = id;
        this.seriesId = seriesId;
        this.title = title;
        this.volumeNumber = volumeNumber;
        this.status = status;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSeriesId() {
        return seriesId;
    }

    public void setSeriesId(Long seriesId) {
        this.seriesId = seriesId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getVolumeNumber() {
        return volumeNumber;
    }

    public void setVolumeNumber(int volumeNumber) {
        this.volumeNumber = volumeNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}