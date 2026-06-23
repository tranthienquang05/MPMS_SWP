package com.example.manga_management.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.manga_management.entity.Assistant;
import com.example.manga_management.repository.AssistantRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/mangaka")
@RequiredArgsConstructor
public class MangakaApiController {

    private final AssistantRepository assistantRepository;

    @GetMapping("/{mangakaId}/assistants")
    public List<Assistant> getAssistants(
            @PathVariable String mangakaId) {

        return assistantRepository.findByMangakaId(mangakaId);
    }
}