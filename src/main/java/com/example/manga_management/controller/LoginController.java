package com.example.manga_management.controller;

import com.example.manga_management.entity.User;
import com.example.manga_management.repository.ProposalRepository;
import com.example.manga_management.repository.TantoEditorRepository;
import com.example.manga_management.service.UserService;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/manga")
public class LoginController {

    private final UserService userService;
    private final ProposalRepository proposalRepository;
    private final TantoEditorRepository tantoEditorRepository;

    // Khai báo Constructor đồng bộ đầy đủ các Repository phụ trợ dữ liệu duyệt bài
    public LoginController(UserService userService, 
                           ProposalRepository proposalRepository, 
                           TantoEditorRepository tantoEditorRepository) {
        this.userService = userService;
        this.proposalRepository = proposalRepository;
        this.tantoEditorRepository = tantoEditorRepository;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String handleLogin(
            @RequestParam String txtUsername,
            @RequestParam String txtPassword,
            HttpSession session,
            Model model) {
        User user = userService.login(txtUsername, txtPassword);

        if (user == null) {
            model.addAttribute("error", "Sai tên đăng nhập hoặc mật khẩu!");
            return "login";
        }

        session.setAttribute("user", user);

        switch (user.getRole().toLowerCase()) {
            case "board":
                return "redirect:/manga/editor";
            case "editor":
                return "redirect:/manga/tantou";
            case "mangaka":
                return "redirect:/manga/mangaka";
            case "assistant":
                return "redirect:/manga/assistant";
            default:
                model.addAttribute("error", "Vai trò người dùng không hợp lệ!");
                return "login";
        }
    }

    @GetMapping("/tantou")
    public String tantouPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/manga/login";
        
        // Đổ dữ liệu các bản thảo đang chờ duyệt lên bảng của trang tantou.html
        model.addAttribute("pendingProposals", proposalRepository.findByStatus("pending"));
        return "tantou";
    }

    @GetMapping("/editor")
    public String editorPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/manga/login";
        
        // Đổ dữ liệu các bản thảo đã qua sơ duyệt lên bảng bình chọn của trang editor.html
        model.addAttribute("votingProposals", proposalRepository.findByStatus("approved_by_tantou"));
        return "editor";
    }

    // ĐÃ XÓA hàm /mangaka tại đây để loại bỏ hoàn toàn lỗi xung đột Route (404/500)

    @GetMapping("/assistant")
    public String assistantPage(HttpSession session) {
        if (session.getAttribute("user") == null) return "redirect:/manga/login";
        return "assistant";
    }

    // API hỗ trợ mở tab mới để đọc file PDF trực tiếp từ thư mục cứng
    @GetMapping("/view-file")
    @ResponseBody
    public org.springframework.core.io.Resource serveFile(@RequestParam("path") String path) throws java.net.MalformedURLException {
        String cleanPath = path.replace("/uploads/", "");
        java.nio.file.Path fileLocation = java.nio.file.Paths.get("D:/LEARNING/SWP391/uploads/").resolve(cleanPath);
        return new org.springframework.core.io.UrlResource(fileLocation.toUri());
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/manga/login";
    }
}