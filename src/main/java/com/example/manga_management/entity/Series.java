package com.example.manga_management.entity;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "series")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"proposal"})
public class Series {

    @Id
    @Column(name = "SeriesID", length = 6)
    private String id;

    @OneToOne
    @JoinColumn(name = "ProposalID", nullable = false)
    private Proposal proposal;

    @Column(name = "BookJacket", length = 30)
    private String bookJacket;

    @Column(name = "SeriesName", nullable = false, length = 30)
    private String seriesName;

    @Column(name = "Description", length = 255)
    private String description;

    @Column(name = "Genre", length = 50)
    private String genre;

    @Column(name = "StartDate")
    private LocalDate startDate;

    @Column(name = "Status", nullable = false, length = 20)
    private String status;

    /**
     * Series bị khoá thao tác: đang chờ hồ sơ bảo vệ (pending_cancel) hoặc đã bị
     * dừng hẳn (stopped). Lúc này không được tạo chapter, tạo trang, giao việc,
     * duyệt bài hay submit chapter.
     */
    public boolean isLocked() {
        return "pending_cancel".equals(status) || "stopped".equals(status);
    }

    /** Lý do bị khoá, dùng làm message trả về cho client. */
    public String getLockMessage() {
        if ("pending_cancel".equals(status)) {
            return "Series đang chờ hồ sơ bảo vệ, không thể thao tác lúc này!";
        }
        if ("stopped".equals(status)) {
            return "Series đã bị dừng phát hành, không thể thao tác!";
        }
        return null;
    }
}