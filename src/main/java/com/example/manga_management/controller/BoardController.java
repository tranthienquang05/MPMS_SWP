package com.example.manga_management.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.example.manga_management.entity.Board;
import com.example.manga_management.entity.Proposal;
import com.example.manga_management.entity.User;
import com.example.manga_management.repository.BoardProposalCommentRepository;
import com.example.manga_management.repository.BoardRepository;
import com.example.manga_management.repository.ProposalRepository;
import com.example.manga_management.service.ProposalService;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/manga/editor")
public class BoardController {

    private final ProposalRepository proposalRepository;
    private final ProposalService proposalService;
    private final NotificationController notificationController;
    private final BoardRepository boardRepository;
    private final BoardProposalCommentRepository boardProposalCommentRepository;

    public BoardController(ProposalRepository proposalRepository, ProposalService proposalService,
            NotificationController notificationController, BoardRepository boardRepository,
            BoardProposalCommentRepository boardProposalCommentRepository) {
        this.proposalRepository = proposalRepository;
        this.proposalService = proposalService;
        this.notificationController = notificationController;
        this.boardRepository = boardRepository;
        this.boardProposalCommentRepository = boardProposalCommentRepository;
    }

    @GetMapping("")
    public String editorPage(HttpSession session, Model model) {
        Object userObj = session.getAttribute("user");
        if (!(userObj instanceof User user))
            return "redirect:/login";
        model.addAttribute("currentUserId", user.getId());
        return "editor";
    }

    @Operation(summary = "Danh sách đề xuất chờ hội đồng bỏ phiếu (kèm 2 file)")
    @GetMapping("/data")
    @ResponseBody
    public Map<String, Object> boardData(HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        Object userObj = session.getAttribute("user");
        if (userObj == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập!");
            return result;
        }

        User user = (User) userObj;
        Board board = boardRepository.findByUser_Id(user.getId()).orElse(null);
        List<Proposal> list = proposalRepository.findByStatus("board_check");

        List<Map<String, Object>> proposalPayload = new ArrayList<>();
        for (Proposal proposal : list) {
            Map<String, Object> proposalData = new HashMap<>();
            proposalData.put("id", proposal.getId());
            proposalData.put("seriesName", proposal.getSeriesName());
            proposalData.put("filePath", proposal.getFilePath());
            proposalData.put("fileOfTantou", proposal.getFileOfTantou());
            proposalData.put("voteCount", boardProposalCommentRepository.countByProposal_Id(proposal.getId()));

            Map<String, Object> mangakaData = new HashMap<>();
            Map<String, Object> userData = new HashMap<>();
            if (proposal.getMangaka() != null && proposal.getMangaka().getUser() != null) {
                userData.put("fullname", proposal.getMangaka().getUser().getFullname());
                mangakaData.put("user", userData);
            }
            proposalData.put("mangaka", mangakaData);

            proposalPayload.add(proposalData);
        }

        result.put("status", "success");
        result.put("userId", user.getId());
        result.put("boardId", board != null ? board.getId() : null);
        result.put("totalBoards", boardRepository.count());
        result.put("total", proposalPayload.size());
        result.put("proposals", proposalPayload);
        return result;
    }

    @Operation(summary = "Bỏ phiếu + comment cho đề xuất")
    @PostMapping("/vote")
    @ResponseBody
    public Map<String, String> boardReview(
            @RequestParam String proposalId,
            @RequestParam String boardId,
            @RequestParam String action, // "pass" | "reject"
            @RequestParam(required = false) String content,
            HttpSession session) {

        Map<String, String> result = new HashMap<>();

        try {
            User user = (User) session.getAttribute("user");
            if (user == null) {
                result.put("status", "error");
                result.put("message", "Chưa đăng nhập!");
                return result;
            }

            Board board = boardRepository.findByUser_Id(user.getId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy thành viên hội đồng của tài khoản hiện tại!"));

            String resolvedBoardId = board.getId();
            if (boardId != null && !boardId.isBlank() && !board.getId().equals(boardId)) {
                resolvedBoardId = board.getId();
            }

            Proposal p = proposalService.submitBoardVote(proposalId, resolvedBoardId, action, content);

            String voteResult = "pass".equals(action) ? "Thông qua" : "Từ chối";

            notificationController.send(null, p.getMangaka().getUser().getId(),
                    "Dự án '" + p.getSeriesName() + "' đã nhận phiếu: " + voteResult,
                    "/manga/mangaka/my-projects");

            notificationController.send("tantou", null,
                    "Dự án '" + p.getSeriesName() + "' đã nhận phiếu: " + voteResult,
                    "/manga/tantou");

            result.put("status", "success");
            result.put("proposalId", proposalId);
            result.put("newStatus", p.getStatus());
            result.put("message", "Đã ghi nhận phiếu bầu: " + voteResult);

        } catch (RuntimeException e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }
}
