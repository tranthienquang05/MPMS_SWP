package com.example.manga_management.controller;

import java.util.HashMap;
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
import com.example.manga_management.repository.FrameTaskRepository;
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

    private final FrameTaskRepository frameTaskRepository;

    private final NotificationController notificationController;

    public AssistantController(AssistantRepository assistantRepository,
            SubmissionRepository submissionRepository,
            MangaPageRepository mangaPageRepository,
            FrameTaskRepository frameTaskRepository,
            NotificationController notificationController) {
        this.assistantRepository = assistantRepository;
        this.submissionRepository = submissionRepository;
        this.mangaPageRepository = mangaPageRepository;
        this.frameTaskRepository = frameTaskRepository;
        this.notificationController = notificationController;
    }

    /**
     * Đếm số frame đã giao cho từng submission, để hiển thị trên kanban card mà
     * không cần mở từng task.
     */
    private Map<String, Integer> buildFrameCounts(List<Submission> todo, List<Submission> waiting,
            List<Submission> done) {
        Map<String, Integer> frameCounts = new HashMap<>();
        for (Submission s : todo) {
            frameCounts.put(s.getId(), frameTaskRepository.findBySubmission_IdOrderByFrameNumberAsc(s.getId()).size());
        }
        for (Submission s : waiting) {
            frameCounts.put(s.getId(), frameTaskRepository.findBySubmission_IdOrderByFrameNumberAsc(s.getId()).size());
        }
        for (Submission s : done) {
            frameCounts.put(s.getId(), frameTaskRepository.findBySubmission_IdOrderByFrameNumberAsc(s.getId()).size());
        }
        return frameCounts;
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

        List<Submission> todo = submissionRepository.findByAssistant_IdAndStatus(assistant.getId(), "intask");
        List<Submission> waiting = submissionRepository.findByAssistant_IdAndStatus(assistant.getId(), "done");
        List<Submission> done = submissionRepository.findByAssistant_IdAndStatus(assistant.getId(), "finish");

        model.addAttribute("todo", todo);
        model.addAttribute("waiting", waiting);
        model.addAttribute("done", done);
        model.addAttribute("frameCounts", buildFrameCounts(todo, waiting, done));
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

        List<Submission> todo = submissionRepository.findByAssistant_IdAndStatus(assistant.getId(), "intask");
        List<Submission> waiting = submissionRepository.findByAssistant_IdAndStatus(assistant.getId(), "done");
        List<Submission> done = submissionRepository.findByAssistant_IdAndStatus(assistant.getId(), "finish");

        model.addAttribute("todo", todo);
        model.addAttribute("waiting", waiting);
        model.addAttribute("done", done);
        model.addAttribute("frameCounts", buildFrameCounts(todo, waiting, done));

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
        model.addAttribute("frameCounts", buildFrameCounts(todo, waiting, done));

        model.addAttribute("submission", submission);
        model.addAttribute("page", submission.getPageId());
        model.addAttribute("activeTab", "tab-draw");

        // Kịch bản chapter + kịch bản trang + note từng frame cho assistant xem
        model.addAttribute("chapterScript",
                submission.getPageId() != null && submission.getPageId().getChapter() != null
                        ? submission.getPageId().getChapter().getScript()
                        : null);
        model.addAttribute("pageScript",
                submission.getPageId() != null ? submission.getPageId().getScript() : null);
        model.addAttribute("frameTasks",
                frameTaskRepository.findBySubmission_IdOrderByFrameNumberAsc(submission.getId()));

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
                "submissionId", submission.getId(),
                "chapterScript",
                submission.getPageId() != null && submission.getPageId().getChapter() != null
                        && submission.getPageId().getChapter().getScript() != null
                                ? submission.getPageId().getChapter().getScript()
                                : "",
                "pageScript",
                submission.getPageId() != null && submission.getPageId().getScript() != null
                        ? submission.getPageId().getScript()
                        : "",
                "frameTasks",
                frameTaskRepository.findBySubmission_IdOrderByFrameNumberAsc(submission.getId())
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

        // Báo cho mangaka biết trợ lý đã nộp bài, đang chờ duyệt
        var chapter = submission.getPageId().getChapter();
        if (chapter != null && chapter.getSeries() != null
                && chapter.getSeries().getProposal() != null
                && chapter.getSeries().getProposal().getMangaka() != null) {
            String mangakaUserId = chapter.getSeries().getProposal().getMangaka().getUser().getId();
            String seriesId = chapter.getSeries().getId();
            notificationController.send(null, mangakaUserId,
                    "Trợ lý " + assistant.getUser().getFullname() + " đã nộp bài trang "
                            + submission.getPageId().getPageNumber() + " (Chapter " + chapter.getChapterNumber()
                            + " - " + chapter.getSeries().getSeriesName() + "), đang chờ bạn duyệt.",
                    "/manga/mangaka/myseries/" + seriesId + "/" + chapter.getId());
        }

        return Map.of("status", "success", "message", "Nộp bài thành công");
    }
}
