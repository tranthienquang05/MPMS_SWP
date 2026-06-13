package com.example.manga_management.model;

public class Task {
    private Long id;
    private Long chapterId; // Giao việc thuộc chapter nào
    private Long assistantId; // Trợ lý nào nhận việc
    private String description;// Mô tả công việc (Ví dụ: Đi nét, xóa nền, đổ bóng)
    private String status; // ASSIGNED, COMPLETED

    public Task() {
    }

    public Task(Long id, Long chapterId, Long assistantId, String description, String status) {
        this.id = id;
        this.chapterId = chapterId;
        this.assistantId = assistantId;
        this.description = description;
        this.status = status;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getChapterId() {
        return chapterId;
    }

    public void setChapterId(Long chapterId) {
        this.chapterId = chapterId;
    }

    public Long getAssistantId() {
        return assistantId;
    }

    public void setAssistantId(Long assistantId) {
        this.assistantId = assistantId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}