package com.example.manga_management.service;

import com.example.manga_management.entity.Assistant;
import com.example.manga_management.entity.Chapter;
import com.example.manga_management.entity.MangaPage;
import com.example.manga_management.entity.Notification;
import com.example.manga_management.entity.Submission;
import com.example.manga_management.entity.User;
import com.example.manga_management.repository.AssistantRepository;
import com.example.manga_management.repository.ChapterRepository;
import com.example.manga_management.repository.MangaPageRepository;
import com.example.manga_management.repository.NotificationRepository;
import com.example.manga_management.repository.SubmissionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String TYPE_GENERIC = "GENERIC";
    private static final String TYPE_DEADLINE_SOON = "DEADLINE_SOON";
    private static final String TYPE_DEADLINE_OVERDUE = "DEADLINE_OVERDUE";
    private static final String TYPE_AUTO_SUBMIT = "AUTO_SUBMIT";
    private static final String TYPE_CHAPTER_OVERDUE_CANCEL = "CHAPTER_OVERDUE_CANCEL";

    private final NotificationRepository notificationRepository;
    private final SubmissionRepository submissionRepository;
    private final AssistantRepository assistantRepository;
    private final MangaPageRepository mangaPageRepository;
    private final ChapterRepository chapterRepository;

    public List<Notification> getInbox(User user) {
        if (user == null) {
            return List.of();
        }
        return notificationRepository.findInbox(user.getId(), user.getRole());
    }

    public long getUnreadCount(User user) {
        if (user == null) {
            return 0L;
        }
        return notificationRepository.countUnread(user.getId(), user.getRole());
    }

    public Notification sendToUser(String userId, String content, String link) {
        return sendToUser(userId, content, link, TYPE_GENERIC, null);
    }

    public Notification sendToUser(String userId, String content, String link, String type, String referenceKey) {
        if (userId == null || userId.isBlank() || content == null || content.isBlank()) {
            return null;
        }

        if (type != null && referenceKey != null
                && notificationRepository.countByUserIdAndTypeAndReferenceKey(userId, type, referenceKey) > 0) {
            return null;
        }

        Notification note = Notification.builder()
                .userId(userId)
                .content(content)
                .link(link)
                .type(type)
                .referenceKey(referenceKey)
                .isRead(false)
                .build();
        return notificationRepository.save(note);
    }

    public Notification sendToRole(String role, String content, String link) {
        return sendToRole(role, content, link, TYPE_GENERIC, null);
    }

    public Notification sendToRole(String role, String content, String link, String type, String referenceKey) {
        if (role == null || role.isBlank() || content == null || content.isBlank()) {
            return null;
        }

        if (type != null && referenceKey != null
                && notificationRepository.countByRoleAndTypeAndReferenceKey(role, type, referenceKey) > 0) {
            return null;
        }

        Notification note = Notification.builder()
                .role(role)
                .content(content)
                .link(link)
                .type(type)
                .referenceKey(referenceKey)
                .isRead(false)
                .build();
        return notificationRepository.save(note);
    }

    public void markAllRead(User user) {
        if (user == null) {
            return;
        }
        List<Notification> list = notificationRepository.findInbox(user.getId(), user.getRole());
        list.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(list);
    }

    public boolean markRead(Long notificationId, User user) {
        Notification note = notificationRepository.findById(notificationId).orElse(null);
        if (note == null || !isVisibleTo(note, user)) {
            return false;
        }
        if (!note.isRead()) {
            note.setRead(true);
            notificationRepository.save(note);
        }
        return true;
    }

    public boolean isVisibleTo(Notification note, User user) {
        if (note == null || user == null) {
            return false;
        }
        return (note.getUserId() != null && note.getUserId().equals(user.getId()))
                || (note.getRole() != null && note.getRole().equalsIgnoreCase(user.getRole()));
    }

    /**
     * Quá hạn deadline thì tự động nộp bài: submission intask -> done, page ->
     * done. Assistant vẫn giữ intask cho tới khi mangaka duyệt hết task.
     */
    @Transactional
    @Scheduled(fixedDelay = 60_000)
    public void autoSubmitOverdueSubmissions() {
        LocalDateTime now = LocalDateTime.now();
        List<Submission> overdueList = submissionRepository.findByStatusAndDeadlineBefore("intask", now);

        for (Submission submission : overdueList) {
            submission.setStatus("done");
            submissionRepository.save(submission);

            if (submission.getPageId() != null) {
                submission.getPageId().setStatus("done");
                mangaPageRepository.save(submission.getPageId());
            }

            Assistant assistant = submission.getAssistant();
            if (assistant != null && assistant.getUser() != null) {
                sendToUser(assistant.getUser().getId(),
                        "Bài làm " + submission.getId() + " đã được tự động nộp do quá hạn deadline ("
                                + submission.getDeadline() + ")",
                        "/manga/assistant",
                        TYPE_AUTO_SUBMIT,
                        submission.getId());
            }
        }
    }

    /**
     * Chapter quá hạn nộp: mọi trang chưa "finish" sẽ tự động bị đánh dấu hoàn
     * thành. Nếu trang đang có task "intask" thì hủy task đó và báo cho tantou.
     */
    @Transactional
    @Scheduled(fixedDelay = 60_000)
    public void autoFinishOverdueChapters() {
        LocalDate today = LocalDate.now();
        List<Chapter> overdueChapters = chapterRepository.findByStatusAndDeadlineBefore("unfinish", today);

        for (Chapter chapter : overdueChapters) {
            List<MangaPage> pages = mangaPageRepository.findByChapter(chapter);
            for (MangaPage page : pages) {
                if ("finish".equals(page.getStatus())) {
                    continue;
                }

                Optional<Submission> subOpt = submissionRepository.findTopByPageIdIdOrderByCreatedAtDesc(page.getId());
                if (subOpt.isPresent() && "intask".equals(subOpt.get().getStatus())) {
                    Submission sub = subOpt.get();
                    sub.setStatus("cancelled");
                    submissionRepository.save(sub);

                    String content = "Task " + sub.getId() + " (trang " + page.getPageNumber()
                            + ", chapter " + chapter.getChapterName() + ") đã bị hủy do chapter quá hạn nộp.";
                    sendToRole("tantou", content, "/manga/editor", TYPE_CHAPTER_OVERDUE_CANCEL, sub.getId());

                    if (sub.getAssistant() != null) {
                        refreshAssistantStatus(sub.getAssistant());
                    }
                }

                page.setStatus("finish");
                mangaPageRepository.save(page);
            }
        }
    }

    /**
     * Chỉ về "untask" khi tất cả task của assistant đã được duyệt hết (không còn
     * submission intask hoặc done chờ duyệt). Trùng logic với PageController để
     * cả 2 luồng (mangaka duyệt / chapter quá hạn hủy task) đều nhất quán.
     */
    private void refreshAssistantStatus(Assistant assistant) {
        boolean hasUnapprovedTask = !submissionRepository
                .findByAssistant_IdAndStatus(assistant.getId(), "intask").isEmpty()
                || !submissionRepository.findByAssistant_IdAndStatus(assistant.getId(), "done").isEmpty();
        assistant.setStatus(hasUnapprovedTask ? "intask" : "untask");
        assistantRepository.save(assistant);
    }

    @Transactional
    @Scheduled(fixedDelay = 3_600_000)
    public void scanDeadlineReminders() {
        LocalDateTime today = LocalDateTime.now();
        LocalDateTime soon = today.plusDays(1);

        remindSubmissions(
                submissionRepository.findByStatusAndDeadlineBetween("intask", today, soon),
                TYPE_DEADLINE_SOON,
                "Sắp đến hạn",
                today,
                false
        );

        remindSubmissions(
                submissionRepository.findByStatusAndDeadlineBefore("intask", today),
                TYPE_DEADLINE_OVERDUE,
                "Đã quá hạn",
                today,
                true
        );
    }

    private void remindSubmissions(List<Submission> submissions, String type, String label, LocalDateTime today,
            boolean overdue) {
        for (Submission submission : submissions) {
            Assistant assistant = submission.getAssistant();
            if (assistant == null || assistant.getUser() == null || submission.getPageId() == null
                    || submission.getPageId().getChapter() == null
                    || submission.getPageId().getChapter().getSeries() == null) {
                continue;
            }

            LocalDateTime deadline = submission.getDeadline();
            if (deadline == null) {
                continue;
            }

            String referenceKey = submission.getId() + ":" + today + ":" + type;
            String seriesName = submission.getPageId().getChapter().getSeries().getSeriesName();
            String chapterLabel = "Chapter " + submission.getPageId().getChapter().getChapterNumber()
                    + ", Page " + submission.getPageId().getPageNumber();
            String content = label + " cho " + seriesName + " - " + chapterLabel
                    + " (deadline " + deadline + ")";
            String link = "/manga/assistant/submission/" + submission.getId() + "/edit";

            sendToUser(assistant.getUser().getId(), content, link, type, referenceKey);
        }
    }
}
