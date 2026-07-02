package com.example.manga_management.controller;

import com.example.manga_management.service.MangaChatService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/chat")
public class MangaChatController {

    @Autowired
    private MangaChatService mangaChatService;

    @PostMapping("/message")
    public ResponseEntity<Map<String, Object>> handleMessage(
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "image", required = false) MultipartFile image,
            HttpSession session) {

        Map<String, Object> response = new HashMap<>();
        
        if (message == null) message = "";
        
        try {
            // Case 3: Image Edit (User uploads image + prompt)
            if (image != null && !image.isEmpty()) {
                String base64Image = mangaChatService.editImage(image, message);
                if (base64Image != null) {
                    response.put("type", "image");
                    response.put("content", base64Image);
                } else {
                    response.put("type", "text");
                    response.put("content", "Lỗi: Không thể chỉnh sửa ảnh.");
                }
            } 
            // Case 2: Create New Image
            else if (isImageGenerationRequest(message)) {
                String base64Image = mangaChatService.generateImage(message);
                if (base64Image != null) {
                    response.put("type", "image");
                    response.put("content", base64Image);
                } else {
                    response.put("type", "text");
                    response.put("content", "Lỗi: Không thể tạo ảnh.");
                }
            } 
            // Case 1: Pure Text
            else {
                String aiText = mangaChatService.generateChatResponse(message);
                response.put("type", "text");
                response.put("content", aiText);
            }

            // Save chat history
            saveToHistory(session, "user", message, image != null);
            saveToHistory(session, "ai", (String) response.get("content"), "image".equals(response.get("type")));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("type", "text");
            response.put("content", "Lỗi hệ thống: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<Map<String, Object>>> getHistory(HttpSession session) {
        List<Map<String, Object>> history = (List<Map<String, Object>>) session.getAttribute("chatHistory");
        if (history == null) {
            history = new ArrayList<>();
        }
        return ResponseEntity.ok(history);
    }
    
    @PostMapping("/clear")
    public ResponseEntity<String> clearHistory(HttpSession session) {
        session.removeAttribute("chatHistory");
        return ResponseEntity.ok("OK");
    }

    private boolean isImageGenerationRequest(String message) {
        String lowerMsg = message.toLowerCase();
        return lowerMsg.contains("vẽ") || 
               lowerMsg.contains("tạo") || 
               lowerMsg.contains("generate") || 
               lowerMsg.contains("draw") || 
               lowerMsg.contains("tạo ảnh") || 
               lowerMsg.contains("vẽ background") || 
               lowerMsg.contains("vẽ nhân vật");
    }

    private void saveToHistory(HttpSession session, String role, String content, boolean isImage) {
        List<Map<String, Object>> history = (List<Map<String, Object>>) session.getAttribute("chatHistory");
        if (history == null) {
            history = new ArrayList<>();
        }
        Map<String, Object> msg = new HashMap<>();
        msg.put("role", role);
        msg.put("content", content);
        msg.put("isImage", isImage);
        history.add(msg);
        session.setAttribute("chatHistory", history);
    }
}
