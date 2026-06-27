package com.example.manga_management.controller;

import com.example.manga_management.entity.*;
import com.example.manga_management.repository.*;

import jakarta.servlet.http.HttpSession;

import java.util.*;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/manga/system-admin")
public class AdminController {

    private final UserRepository userRepository;
    private final MangakaRepository mangakaRepository;
    private final AssistantRepository assistantRepository;
    private final TantoEditorRepository tantoEditorRepository;
    private final BoardRepository boardRepository;

    public AdminController(UserRepository userRepository,
            MangakaRepository mangakaRepository,
            AssistantRepository assistantRepository,
            TantoEditorRepository tantoEditorRepository,
            BoardRepository boardRepository) {
        this.userRepository = userRepository;
        this.mangakaRepository = mangakaRepository;
        this.assistantRepository = assistantRepository;
        this.tantoEditorRepository = tantoEditorRepository;
        this.boardRepository = boardRepository;
    }

    @GetMapping("")
    public String adminPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"admin".equalsIgnoreCase(user.getRole())) {
            return "redirect:/login";
        }
        return "admin";
    }

    @GetMapping("/data")
    @ResponseBody
    public Map<String, Object> adminData(HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        User user = (User) session.getAttribute("user");
        if (user == null || !"admin".equalsIgnoreCase(user.getRole())) {
            result.put("status", "error");
            result.put("message", "Không có quyền truy cập");
            return result;
        }

        result.put("status", "success");
        result.put("allUsers", userRepository.findAll());
        result.put("tantoEditors", tantoEditorRepository.findAll());
        result.put("boards", boardRepository.findAll());

        // Build mangakas với editor info đầy đủ
        List<Map<String, Object>> mangakaList = new ArrayList<>();
        for (Mangaka m : mangakaRepository.findAll()) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", m.getId());
            item.put("user", m.getUser());
            if (m.getEditor() != null) {
                Map<String, Object> editorMap = new HashMap<>();
                editorMap.put("id", m.getEditor().getId());
                editorMap.put("user", m.getEditor().getUser());
                item.put("editor", editorMap);
            }
            mangakaList.add(item);
        }
        result.put("mangakas", mangakaList);

        // Build assistants với mangaka.user đầy đủ
        List<Map<String, Object>> assistantList = new ArrayList<>();
        for (Assistant a : assistantRepository.findAll()) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", a.getId());
            item.put("user", a.getUser());
            item.put("salaryPerTask", a.getSalaryPerTask());
            if (a.getMangaka() != null) {
                Map<String, Object> mgkMap = new HashMap<>();
                mgkMap.put("id", a.getMangaka().getId());
                mgkMap.put("user", a.getMangaka().getUser());
                item.put("mangaka", mgkMap);
            }
            assistantList.add(item);
        }
        result.put("assistants", assistantList);

        return result;
    }

    @PostMapping("/accounts/create")
    @ResponseBody
    public Map<String, Object> createAccount(@RequestBody Map<String, String> body,
            HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        try {
            // Kiểm tra quyền
            User admin = (User) session.getAttribute("user");
            if (admin == null || !"admin".equalsIgnoreCase(admin.getRole())) {
                result.put("status", "error");
                result.put("message", "Không có quyền");
                return result;
            }

            // Kiểm tra username trùng
            if (userRepository.findByUsername(body.get("username")).isPresent()) {
                result.put("status", "error");
                result.put("message", "Username đã tồn tại");
                return result;
            }

            // Tạo User
            String newId = String.format("%06d", userRepository.count() + 1);
            User newUser = new User();
            newUser.setId(newId);
            newUser.setUsername(body.get("username"));
            newUser.setPassword(body.get("password"));
            newUser.setFullname(body.getOrDefault("fullname", ""));
            newUser.setEmail(body.getOrDefault("email", ""));
            newUser.setRole(body.get("role"));
            userRepository.save(newUser);

            // Tạo profile tương ứng với role
            String role = body.get("role");
            String profileId = String.format("%06d", getNextProfileId(role));

            switch (role.toLowerCase()) {
                case "mangaka" -> {
                    // Mangaka cần chỉ định TantoEditor
                    String editorId = body.get("editorId");
                    TantoEditor editor = tantoEditorRepository.findById(editorId).orElse(null);
                    if (editor == null) {
                        result.put("status", "error");
                        result.put("message", "Không tìm thấy TantoEditor với ID: " + editorId);
                        return result;
                    }
                    Mangaka mangaka = new Mangaka();
                    mangaka.setId(profileId);
                    mangaka.setUser(newUser);
                    mangaka.setEditor(editor);
                    mangakaRepository.save(mangaka);
                }
                case "assistant" -> {
                    // Assistant cần chỉ định Mangaka
                    String mangakaId = body.get("mangakaId");
                    Mangaka mangaka = mangakaRepository.findById(mangakaId).orElse(null);
                    if (mangaka == null) {
                        result.put("status", "error");
                        result.put("message", "Không tìm thấy Mangaka với ID: " + mangakaId);
                        return result;
                    }
                    Assistant assistant = new Assistant();
                    assistant.setId(profileId);
                    assistant.setUser(newUser);
                    assistant.setMangaka(mangaka);
                    assistant.setSalaryPerTask(
                            Integer.parseInt(body.getOrDefault("salaryPerTask", "0")));
                    assistantRepository.save(assistant);
                }
                case "tantou" -> {
                    TantoEditor editor = new TantoEditor();
                    editor.setId(profileId);
                    editor.setUser(newUser);
                    tantoEditorRepository.save(editor);
                }
                case "board" -> {
                    Board board = new Board();
                    board.setId(profileId);
                    board.setUser(newUser);
                    boardRepository.save(board);
                }
                // "admin" không cần profile riêng
            }

            result.put("status", "success");
            result.put("user", newUser);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    @PostMapping("/accounts/update")
    @ResponseBody
    public Map<String, Object> updateAccount(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            User u = userRepository.findById(body.get("id")).orElse(null);
            if (u == null) {
                result.put("status", "error");
                result.put("message", "Không tìm thấy user với ID: " + body.get("id"));
                return result;
            }

            if (isSet(body, "username"))
                u.setUsername(body.get("username"));
            if (isSet(body, "fullname"))
                u.setFullname(body.get("fullname"));
            if (isSet(body, "email"))
                u.setEmail(body.get("email"));
            if (isSet(body, "password"))
                u.setPassword(body.get("password"));
            // Không cho đổi role vì liên quan đến profile table

            userRepository.save(u);

            // Nếu là assistant và muốn đổi mangaka
            if (isSet(body, "mangakaId")) {
                Assistant assistant = assistantRepository.findByUserId(u.getId()).orElse(null);
                Mangaka mangaka = mangakaRepository.findById(body.get("mangakaId")).orElse(null);
                if (assistant != null && mangaka != null) {
                    assistant.setMangaka(mangaka);
                    if (isSet(body, "salaryPerTask")) {
                        assistant.setSalaryPerTask(Integer.parseInt(body.get("salaryPerTask")));
                    }
                    assistantRepository.save(assistant);
                }
            }

            // Nếu là mangaka và muốn đổi tantou
            if (isSet(body, "editorId")) {
                Mangaka mangaka = mangakaRepository.findByUserId(u.getId()).orElse(null);
                TantoEditor editor = tantoEditorRepository.findById(body.get("editorId")).orElse(null);
                if (mangaka != null && editor != null) {
                    mangaka.setEditor(editor);
                    mangakaRepository.save(mangaka);
                }
            }

            result.put("status", "success");
            result.put("user", u);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    @PostMapping("/accounts/delete")
    @ResponseBody
    public Map<String, Object> deleteAccount(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            String id = body.get("id");
            User u = userRepository.findById(id).orElse(null);
            if (u == null) {
                result.put("status", "error");
                result.put("message", "Không tìm thấy user với ID: " + id);
                return result;
            }

            // Xóa profile trước, sau đó xóa user
            switch (u.getRole().toLowerCase()) {
                case "mangaka" -> mangakaRepository.findByUserId(id)
                        .ifPresent(mangakaRepository::delete);
                case "assistant" -> assistantRepository.findByUserId(id)
                        .ifPresent(assistantRepository::delete);
                case "tantou" -> tantoEditorRepository.findByUserId(id)
                        .ifPresent(tantoEditorRepository::delete);
                case "board" -> boardRepository.findByUserId(id)
                        .ifPresent(boardRepository::delete);
            }

            userRepository.deleteById(id);
            result.put("status", "success");
            result.put("deletedId", id);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    // ══════════════════════════════════════════════════════════════
    // ĐỔI QUAN HỆ (riêng biệt với update thông tin cơ bản)
    // ══════════════════════════════════════════════════════════════

    // Đổi Mangaka của Assistant
    @PostMapping("/assign/assistant-mangaka")
    @ResponseBody
    public Map<String, Object> assignAssistantToMangaka(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            Assistant assistant = assistantRepository.findById(body.get("assistantId")).orElse(null);
            Mangaka mangaka = mangakaRepository.findById(body.get("mangakaId")).orElse(null);

            if (assistant == null) {
                result.put("status", "error");
                result.put("message", "Không tìm thấy Assistant");
                return result;
            }
            if (mangaka == null) {
                result.put("status", "error");
                result.put("message", "Không tìm thấy Mangaka");
                return result;
            }

            assistant.setMangaka(mangaka);
            assistantRepository.save(assistant);

            result.put("status", "success");
            result.put("assistantId", assistant.getId());
            result.put("assistantName", assistant.getUser().getFullname());
            result.put("mangakaId", mangaka.getId());
            result.put("mangakaName", mangaka.getUser().getFullname());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    // Đổi TantoEditor của Mangaka
    @PostMapping("/assign/mangaka-tanto")
    @ResponseBody
    public Map<String, Object> assignMangakaToTanto(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            Mangaka mangaka = mangakaRepository.findById(body.get("mangakaId")).orElse(null);
            TantoEditor editor = tantoEditorRepository.findById(body.get("editorId")).orElse(null);

            if (mangaka == null) {
                result.put("status", "error");
                result.put("message", "Không tìm thấy Mangaka");
                return result;
            }
            if (editor == null) {
                result.put("status", "error");
                result.put("message", "Không tìm thấy TantoEditor");
                return result;
            }

            mangaka.setEditor(editor);
            mangakaRepository.save(mangaka);

            result.put("status", "success");
            result.put("mangakaId", mangaka.getId());
            result.put("mangakaName", mangaka.getUser().getFullname());
            result.put("editorId", editor.getId());
            result.put("editorName", editor.getUser().getFullname());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    // ── Helper ────────────────────────────────────────────────────
    private boolean isSet(Map<String, String> body, String key) {
        return body.containsKey(key) && body.get(key) != null && !body.get(key).isBlank();
    }

    private long getNextProfileId(String role) {
        return switch (role.toLowerCase()) {
            case "mangaka" -> mangakaRepository.count() + 1;
            case "assistant" -> assistantRepository.count() + 1;
            case "tantou" -> tantoEditorRepository.count() + 1;
            case "board" -> boardRepository.count() + 1;
            default -> 0;
        };
    }
}