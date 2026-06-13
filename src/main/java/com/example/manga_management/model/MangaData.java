package com.example.manga_management.model;

import java.util.ArrayList;
import java.util.List;

public class MangaData {
    // Sử dụng static để dữ liệu được lưu trữ tập trung và không bị mất đi khi chuyển đổi qua lại giữa các Controller
    public static List<MangaSeries> seriesList = new ArrayList<>();
    public static List<Chapter> chapterList = new ArrayList<>();
    
    public static long seriesIdCounter = 1;
    public static long chapterIdCounter = 1;

    // Khối static chạy ngay khi ứng dụng khởi động để nạp dữ liệu mẫu ban đầu
    static {
        seriesList.add(new MangaSeries(seriesIdCounter++, "Đảo Hải Tặc", "Hành trình tìm kho báu", 101L, "DRAFT"));
        seriesList.add(new MangaSeries(seriesIdCounter++, "Đại Chiến Titan", "Cuộc chiến sinh tồn", 102L, "APPROVED"));
        chapterList.add(new Chapter(chapterIdCounter++, 2L, "Chapter 1: Khởi đầu", 1, "SKETCHING"));
    }
}