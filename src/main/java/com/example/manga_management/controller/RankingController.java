package com.example.manga_management.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.manga_management.repository.RankingRepository;

@RestController
@RequestMapping("/manga/ranking")
public class RankingController {

    private final RankingRepository rankingRepository;

    public RankingController(RankingRepository rankingRepository) {
        this.rankingRepository = rankingRepository;
    }

    @GetMapping
    public List<Map<String, Object>> getRanking(
            @RequestParam(defaultValue = "0") int month,
            @RequestParam(defaultValue = "2026") int year) {

        List<Object[]> rows;

        if (month == 0) {
            rows = rankingRepository.findRankingByYear(year);
        } else {
            rows = rankingRepository.findRankingByMonthAndYear(month, year);
        }

        List<Map<String, Object>> rankings = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Object[] row = rows.get(i);
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("rank",       i + 1);
            map.put("seriesId",   row[0]);
            map.put("seriesName", row[1]);
            map.put("totalVotes", row[2]);
            rankings.add(map);
        }

        return rankings;
    }
}