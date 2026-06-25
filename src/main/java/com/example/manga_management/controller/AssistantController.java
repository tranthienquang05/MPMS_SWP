package com.example.manga_management.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.manga_management.entity.Assistant;
import com.example.manga_management.entity.Submission;
import com.example.manga_management.entity.User;
import com.example.manga_management.repository.AssistantRepository;
import com.example.manga_management.repository.SubmissionRepository;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/manga/assistant")
public class AssistantController {

    @Autowired
    private final AssistantRepository assistantRepository;

    private final SubmissionRepository submissionRepository;

    public AssistantController(AssistantRepository assistantRepository, SubmissionRepository submissionRepository) {
        this.assistantRepository = assistantRepository;
        this.submissionRepository = submissionRepository;
    }

    // ================= HOME =================
    @GetMapping("")
    public String assistantHome(Model model, HttpSession session) {

        User user = (User) session.getAttribute("user");

        if (user == null) {
            return "redirect:/login";
        }

        Assistant assistant = assistantRepository.findByUserId(user.getId()).orElse(null);

        if (assistant == null) {
            model.addAttribute("todo", List.of());
            model.addAttribute("waiting", List.of());
            model.addAttribute("done", List.of());
            return "assistant";
        }

        model.addAttribute("todo", submissionRepository.findByAssistant_IdAndStatus(assistant.getId(), "unfinish"));

        model.addAttribute("waiting", submissionRepository.findByAssistant_IdAndStatus(assistant.getId(), "finish"));

        model.addAttribute("done", submissionRepository.findByAssistant_IdAndStatus(assistant.getId(), "pass"));
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

        model.addAttribute("todo", submissionRepository.findByAssistant_IdAndStatus(assistant.getId(), "unfinish"));

        model.addAttribute("waiting", submissionRepository.findByAssistant_IdAndStatus(assistant.getId(), "finish"));

        model.addAttribute("done", submissionRepository.findByAssistant_IdAndStatus(assistant.getId(), "pass"));

        model.addAttribute("submissions", submissions);
        model.addAttribute("activeTab", "tab-project");

        return "assistant";
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

        List<Submission> todo = submissionRepository.findByAssistant_IdAndStatus(assistant.getId(), "unfinish");

        List<Submission> waiting = submissionRepository.findByAssistant_IdAndStatus(assistant.getId(), "finish");

        List<Submission> done = submissionRepository.findByAssistant_IdAndStatus(assistant.getId(), "pass");

        Submission submission = submissionRepository.findById(id).orElse(null);

        if (submission == null) {
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

    // ================= UPDATE STATUS =================
    @PostMapping("/submission/{id}/submit")
    public String updateStatus(@PathVariable String id, @RequestParam String status, Model model, HttpSession session) {

        Submission submission = submissionRepository.findById(id).orElse(null);

        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        Assistant assistant = assistantRepository.findByUserId(user.getId()).orElse(null);
        if (assistant == null) {
            return "redirect:/manga/assistant";
        }

        if (submission != null) {
            submission.setStatus(status);
            submissionRepository.save(submission);
        }
        List<Submission> todo = submissionRepository.findByAssistant_IdAndStatus(assistant.getId(), "unfinish");

        List<Submission> waiting = submissionRepository.findByAssistant_IdAndStatus(assistant.getId(), "finish");

        List<Submission> done = submissionRepository.findByAssistant_IdAndStatus(assistant.getId(), "pass");

        if (submission == null) {
            return "redirect:/manga/assistant";
        }
        model.addAttribute("todo", todo);
        model.addAttribute("waiting", waiting);
        model.addAttribute("done", done);

        model.addAttribute("activeTab", "tab-project");
        return "assistant";
    }
}
