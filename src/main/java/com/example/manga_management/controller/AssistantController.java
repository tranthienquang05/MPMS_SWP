package com.example.manga_management.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import io.swagger.v3.oas.annotations.Operation;

import com.example.manga_management.entity.Assistant;
import com.example.manga_management.entity.Submission;
import com.example.manga_management.entity.User;
import com.example.manga_management.repository.AssistantRepository;
import com.example.manga_management.repository.MangaPageRepository;
import com.example.manga_management.repository.SubmissionRepository;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/manga/assistant")
public class AssistantController {

    @Autowired
    private final AssistantRepository assistantRepository;

    private final MangaPageRepository mangaPageRepository;

    private final SubmissionRepository submissionRepository;

    public AssistantController(AssistantRepository assistantRepository,
            SubmissionRepository submissionRepository,
            MangaPageRepository mangaPageRepository) {
        this.assistantRepository = assistantRepository;
        this.submissionRepository = submissionRepository;
        this.mangaPageRepository = mangaPageRepository;
    }

    // ================= HOME =================
    @GetMapping("")
    public String assistantHome(Model model, HttpSession session) {

        User user = (User) session.getAttribute("user");

        if (user == null) {
            return "redirect:/login";
        }

        model.addAttribute("currentUserId", user.getId());

        Assistant assistant = assistantRepository.findByUserId(user.getId()).orElse(null);

        if (assistant == null) {
            model.addAttribute("todo", List.of());
            model.addAttribute("waiting", List.of());
            model.addAttribute("done", List.of());
            return "assistant";
        }

        model.addAttribute("todo", submissionRepository.findByAssistant_IdAndStatus(assistant.getId(), "intask"));

        model.addAttribute("waiting", submissionRepository.findByAssistant_IdAndStatus(assistant.getId(), "done"));

        model.addAttribute("done", submissionRepository.findByAssistant_IdAndStatus(assistant.getId(), "finish"));
        if (model.getAttribute("activeTab") == null) {
            model.addAttribute("activeTab", "tab-home");
        }

        return "assistant";
    }

    // ================= VIEW ALL =================
    @GetMapping("/submission/view")
    public String viewSubmissions(Model model, HttpSession session) {

        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        Assistant assistant = assistantRepository.findByUserId(user.getId()).orElse(null);
        if (assistant == null) {
            model.addAttribute("submissions", List.of());
            model.addAttribute("activeTab", "tab-project");
            model.addAttribute("todo", List.of());
            model.addAttribute("waiting", List.of());
            model.addAttribute("done", List.of());
            return "assistant";
        }

        List<Submission> submissions = submissionRepository.findByAssistant_Id(assistant.getId());

        model.addAttribute("todo", submissionRepository.findByAssistant_IdAndStatus(assistant.getId(), "intask"));

        model.addAttribute("waiting", submissionRepository.findByAssistant_IdAndStatus(assistant.getId(), "done"));

        model.addAttribute("done", submissionRepository.findByAssistant_IdAndStatus(assistant.getId(), "finish"));

        model.addAttribute("submissions", submissions);
        model.addAttribute("activeTab", "tab-project");

        return "assistant";
    }

    @Operation(summary = "[SWAGGER] Lấy danh sách submission của assistant")
    @GetMapping("/submission/view/data")
    @ResponseBody
    public Map<String, Object> viewSubmissionsData(HttpSession session) {

        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Map.of("status", "error", "message", "Vui lòng đăng nhập lại");
        }

        Assistant assistant = assistantRepository.findByUserId(user.getId()).orElse(null);
        if (assistant == null) {
            return Map.of("status", "error", "message", "Không tìm thấy trợ lý");
        }

        List<Submission> todo = submissionRepository.findByAssistant_IdAndStatus(assistant.getId(), "intask");
        List<Submission> waiting = submissionRepository.findByAssistant_IdAndStatus(assistant.getId(), "done");
        List<Submission> done = submissionRepository.findByAssistant_IdAndStatus(assistant.getId(), "finish");

        return Map.of(
                "status", "success",
                "todo", todo,
                "waiting", waiting,
                "done", done
        );
    }

    // ================= EDIT =================
    @GetMapping("/submission/{id}/edit")
    public String editSubmission(@PathVariable String id, Model model, HttpSession session) {

        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        Assistant assistant = assistantRepository.findByUserId(user.getId()).orElse(null);
        if (assistant == null) {
            return "redirect:/manga/assistant";
        }

        List<Submission> todo = submissionRepository.findByAssistant_IdAndStatus(assistant.getId(), "intask");

        List<Submission> waiting = submissionRepository.findByAssistant_IdAndStatus(assistant.getId(), "done");

        List<Submission> done = submissionRepository.findByAssistant_IdAndStatus(assistant.getId(), "finish");

        Submission submission = submissionRepository.findById(id).orElse(null);

        if (submission == null) {
            return "redirect:/manga/assistant";
        }

        if (submission.getAssistant() == null || !assistant.getId().equals(submission.getAssistant().getId())) {
            return "redirect:/manga/assistant";
        }

        model.addAttribute("todo", todo);
        model.addAttribute("waiting", waiting);
        model.addAttribute("done", done);

        model.addAttribute("submission", submission);
        model.addAttribute("page", submission.getPageId());
        model.addAttribute("activeTab", "tab-draw");

        return "assistant";
    }

    @Operation(summary = "[SWAGGER] Lấy thông tin submission để mở màn vẽ")
    @GetMapping("/submission/{id}/edit/data")
    @ResponseBody
    public Map<String, Object> editSubmissionData(@PathVariable String id, HttpSession session) {

        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Map.of("status", "error", "message", "Vui lòng đăng nhập lại");
        }

        Assistant assistant = assistantRepository.findByUserId(user.getId()).orElse(null);
        if (assistant == null) {
            return Map.of("status", "error", "message", "Không tìm thấy trợ lý");
        }

        Submission submission = submissionRepository.findById(id).orElse(null);
        if (submission == null) {
            return Map.of("status", "error", "message", "Không tìm thấy bài nộp");
        }

        if (submission.getAssistant() == null || !assistant.getId().equals(submission.getAssistant().getId())) {
            return Map.of("status", "error", "message", "Bài nộp không hợp lệ hoặc không có quyền");
        }

        return Map.of(
                "status", "success",
                "submission", submission,
                "page", submission.getPageId(),
                "submissionId", submission.getId()
        );
    }

    // ================= UPDATE STATUS =================
    @Operation(summary = "[SWAGGER] Trợ lý nộp bài")
    @PostMapping("/submission/{id}/submit")
    @ResponseBody
    public Map<String, Object> updateStatus(@PathVariable String id, @RequestParam String status, HttpSession session) {

        Submission submission = submissionRepository.findById(id).orElse(null);

        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Map.of("status", "error", "message", "Vui lòng đăng nhập lại");
        }

        Assistant assistant = assistantRepository.findByUserId(user.getId()).orElse(null);
        if (assistant == null) {
            return Map.of("status", "error", "message", "Không tìm thấy trợ lý");
        }

        if (submission == null || submission.getAssistant() == null
                || !assistant.getId().equals(submission.getAssistant().getId())) {
            return Map.of("status", "error", "message", "Bài nộp không hợp lệ hoặc không có quyền");
        }

        if (!"done".equalsIgnoreCase(status)) {
            return Map.of("status", "error", "message", "Trạng thái không hợp lệ");
        }

        submission.setStatus("done");
        submissionRepository.save(submission);
        // Đổi page sang done
        submission.getPageId().setStatus("done");
        mangaPageRepository.save(submission.getPageId());

        return Map.of("status", "success", "message", "Nộp bài thành công");
    }
}
