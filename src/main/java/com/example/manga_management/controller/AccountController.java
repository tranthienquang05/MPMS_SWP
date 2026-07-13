package com.example.manga_management.controller;

import com.example.manga_management.dto.ApiResult;
import com.example.manga_management.entity.User;
import com.example.manga_management.repository.UserRepository;
import com.example.manga_management.service.EmailService;
import com.example.manga_management.service.OtpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/account")
@Tag(name = "Account", description = "Quản lý tài khoản: xác thực OTP và đổi mật khẩu")
public class AccountController {

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final EmailService emailService;

    public AccountController(UserRepository userRepository,
                             OtpService otpService,
                             EmailService emailService) {
        this.userRepository = userRepository;
        this.otpService = otpService;
        this.emailService = emailService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Endpoint 1: Send OTP
    // ─────────────────────────────────────────────────────────────────────────
    @Operation(
        summary = "Gửi OTP xác thực tới email",
        description = "Gửi mã OTP 6 số tới email đã liên kết với tài khoản đang đăng nhập. OTP có hiệu lực 5 phút."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200", description = "Gửi OTP thành công",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ApiResult.class))
        ),
        @ApiResponse(
            responseCode = "400", description = "Email không hợp lệ hoặc chưa có email",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ApiResult.class))
        ),
        @ApiResponse(
            responseCode = "401", description = "Chưa đăng nhập",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ApiResult.class))
        )
    })
    @PostMapping("/send-otp")
    public ResponseEntity<ApiResult> sendOtp(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return ResponseEntity.status(401)
                    .body(new ApiResult("error", "Chưa đăng nhập"));
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ApiResult("error", "Tài khoản chưa có email"));
        }
        try {
            String otp = otpService.generateOtp(user.getId());
            emailService.sendOtpEmail(user.getEmail(), otp);
            return ResponseEntity.ok(new ApiResult("success", "Đã gửi OTP tới email của bạn"));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(new ApiResult("error", "Không thể gửi email: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Endpoint 2: Verify OTP
    // ─────────────────────────────────────────────────────────────────────────
    @Operation(
        summary = "Xác thực mã OTP",
        description = "Kiểm tra mã OTP người dùng nhập. Nếu đúng và còn hạn, đánh dấu session đã xác thực để cho phép đổi mật khẩu."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200", description = "OTP hợp lệ",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ApiResult.class))
        ),
        @ApiResponse(
            responseCode = "400", description = "OTP sai hoặc đã hết hạn",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ApiResult.class))
        ),
        @ApiResponse(
            responseCode = "401", description = "Chưa đăng nhập",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ApiResult.class))
        )
    })
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResult> verifyOtp(@RequestBody Map<String, String> body,
                                               HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return ResponseEntity.status(401)
                    .body(new ApiResult("error", "Chưa đăng nhập"));
        }
        String otp = body.get("otp");
        boolean valid = otpService.verifyOtp(user.getId(), otp);
        if (valid) {
            session.setAttribute("otpVerified", true);
            return ResponseEntity.ok(new ApiResult("success", "Xác thực OTP thành công"));
        } else {
            return ResponseEntity.badRequest()
                    .body(new ApiResult("error", "OTP không đúng hoặc đã hết hạn"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Endpoint 3: Change Password
    // ─────────────────────────────────────────────────────────────────────────
    @Operation(
        summary = "Đổi mật khẩu sau khi xác thực OTP",
        description = "Cập nhật mật khẩu mới. Yêu cầu đã verify OTP trước đó trong cùng session. Mật khẩu tối thiểu 6 ký tự."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200", description = "Đổi mật khẩu thành công",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ApiResult.class))
        ),
        @ApiResponse(
            responseCode = "400", description = "Mật khẩu không hợp lệ hoặc không khớp",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ApiResult.class))
        ),
        @ApiResponse(
            responseCode = "403", description = "Chưa xác thực OTP",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ApiResult.class))
        ),
        @ApiResponse(
            responseCode = "401", description = "Chưa đăng nhập",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ApiResult.class))
        )
    })
    @PostMapping("/change-password")
    public ResponseEntity<ApiResult> changePassword(@RequestBody Map<String, String> body,
                                                    HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return ResponseEntity.status(401)
                    .body(new ApiResult("error", "Chưa đăng nhập"));
        }

        Boolean otpVerified = (Boolean) session.getAttribute("otpVerified");
        if (!Boolean.TRUE.equals(otpVerified)) {
            return ResponseEntity.status(403)
                    .body(new ApiResult("error", "Chưa xác thực OTP"));
        }

        String newPassword = body.get("newPassword");
        String confirmPassword = body.get("confirmPassword");

        if (newPassword == null || newPassword.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ApiResult("error", "Mật khẩu không được để trống"));
        }
        if (newPassword.length() < 6) {
            return ResponseEntity.badRequest()
                    .body(new ApiResult("error", "Mật khẩu phải có ít nhất 6 ký tự"));
        }
        if (!newPassword.equals(confirmPassword)) {
            return ResponseEntity.badRequest()
                    .body(new ApiResult("error", "Mật khẩu nhập lại không khớp"));
        }

        user.setPassword(newPassword);
        User savedUser = userRepository.save(user);

        session.removeAttribute("otpVerified");
        session.setAttribute("user", savedUser);

        return ResponseEntity.ok(new ApiResult("success", "Đổi mật khẩu thành công"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Endpoint 4: Get Profile
    // ─────────────────────────────────────────────────────────────────────────
    @Operation(summary = "[SWAGGER] Lấy thông tin cá nhân user đang đăng nhập")
    @GetMapping("/profile")
    @ResponseBody
    public Map<String, Object> getProfile(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập");
            return result;
        }
        result.put("status", "success");
        result.put("id", user.getId());
        result.put("fullname", user.getFullname());
        result.put("email", user.getEmail());
        result.put("phone", user.getPhone());
        result.put("socialLinks", user.getSocialLinks());
        result.put("emailVerified", user.isEmailVerified());
        result.put("role", user.getRole());
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Endpoint 5: Send OTP xác thực email lần đầu
    // ─────────────────────────────────────────────────────────────────────────
    @Operation(summary = "[SWAGGER] Gửi OTP xác thực email lần đầu đăng nhập")
    @PostMapping("/verify-email")
    @ResponseBody
    public Map<String, Object> verifyEmail(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập");
            return result;
        }
        if (user.isEmailVerified()) {
            result.put("status", "error");
            result.put("message", "Email đã được xác thực");
            return result;
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            result.put("status", "error");
            result.put("message", "Tài khoản chưa có email");
            return result;
        }
        try {
            String otp = otpService.generateOtp(user.getId() + "_verify");
            emailService.sendOtpEmail(user.getEmail(), otp);
            result.put("status", "success");
            result.put("message", "Đã gửi OTP tới " + user.getEmail());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Không thể gửi email: " + e.getMessage());
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Endpoint 6: Confirm OTP xác thực email lần đầu
    // ─────────────────────────────────────────────────────────────────────────
    @Operation(summary = "[SWAGGER] Xác nhận OTP xác thực email lần đầu")
    @PostMapping("/confirm-email-otp")
    @ResponseBody
    public Map<String, Object> confirmEmailOtp(@RequestBody Map<String, String> body,
                                               HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập");
            return result;
        }
        if (user.isEmailVerified()) {
            result.put("status", "error");
            result.put("message", "Email đã được xác thực");
            return result;
        }
        String otp = body.get("otp");
        if (otp == null || otp.isBlank()) {
            result.put("status", "error");
            result.put("message", "Vui lòng nhập mã OTP");
            return result;
        }
        boolean valid = otpService.verifyOtp(user.getId() + "_verify", otp);
        if (!valid) {
            result.put("status", "error");
            result.put("message", "OTP không đúng hoặc đã hết hạn");
            return result;
        }
        try {
            user.setEmailVerified(true);
            User savedUser = userRepository.save(user);
            session.setAttribute("user", savedUser);
            result.put("status", "success");
            result.put("message", "Xác thực email thành công");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Endpoint 7: Send OTP đổi email
    // ─────────────────────────────────────────────────────────────────────────
    @Operation(summary = "[SWAGGER] Gửi OTP tới email mới để đổi email")
    @PostMapping("/send-change-email-otp")
    @ResponseBody
    public Map<String, Object> sendChangeEmailOtp(@RequestBody Map<String, String> body,
                                                  HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập");
            return result;
        }
        String newEmail = body.get("newEmail");
        if (newEmail == null || newEmail.isBlank()) {
            result.put("status", "error");
            result.put("message", "Vui lòng nhập email mới");
            return result;
        }
        if (newEmail.equalsIgnoreCase(user.getEmail())) {
            result.put("status", "error");
            result.put("message", "Email mới không được trùng email hiện tại");
            return result;
        }
        if (userRepository.findByEmail(newEmail).isPresent()) {
            result.put("status", "error");
            result.put("message", "Email này đã được sử dụng bởi tài khoản khác");
            return result;
        }
        try {
            session.setAttribute("pendingEmail", newEmail);
            String otp = otpService.generateOtp(user.getId() + "_changeEmail");
            emailService.sendOtpEmail(newEmail, otp);
            result.put("status", "success");
            result.put("message", "Đã gửi OTP tới " + newEmail);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Không thể gửi email: " + e.getMessage());
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Endpoint 8: Confirm OTP đổi email
    // ─────────────────────────────────────────────────────────────────────────
    @Operation(summary = "[SWAGGER] Xác nhận OTP và lưu email mới")
    @PostMapping("/confirm-change-email-otp")
    @ResponseBody
    public Map<String, Object> confirmChangeEmailOtp(@RequestBody Map<String, String> body,
                                                     HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập");
            return result;
        }
        String pendingEmail = (String) session.getAttribute("pendingEmail");
        if (pendingEmail == null || pendingEmail.isBlank()) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy email đang chờ xác thực. Vui lòng thử lại");
            return result;
        }
        String otp = body.get("otp");
        if (otp == null || otp.isBlank()) {
            result.put("status", "error");
            result.put("message", "Vui lòng nhập mã OTP");
            return result;
        }
        boolean valid = otpService.verifyOtp(user.getId() + "_changeEmail", otp);
        if (!valid) {
            result.put("status", "error");
            result.put("message", "OTP không đúng hoặc đã hết hạn");
            return result;
        }
        try {
            user.setEmail(pendingEmail);
            user.setEmailVerified(true);
            User savedUser = userRepository.save(user);
            session.setAttribute("user", savedUser);
            session.removeAttribute("pendingEmail");
            result.put("status", "success");
            result.put("message", "Đổi email thành công");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Endpoint 9: Update profile
    // ─────────────────────────────────────────────────────────────────────────
    @Operation(summary = "[SWAGGER] Cập nhật thông tin cá nhân (nickname, phone, socialLinks)")
    @PostMapping("/update-profile")
    @ResponseBody
    public Map<String, Object> updateProfile(@RequestBody Map<String, String> body,
                                             HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập");
            return result;
        }
        String nickname = body.get("nickname");
        String phone = body.get("phone");
        String socialLinks = body.get("socialLinks");
        if (nickname != null && !nickname.isBlank()) {
            user.setFullname(nickname);
        }
        if (phone != null) {
            user.setPhone(phone);
        }
        if (socialLinks != null) {
            user.setSocialLinks(socialLinks);
        }
        try {
            User savedUser = userRepository.save(user);
            session.setAttribute("user", savedUser);
            result.put("status", "success");
            result.put("message", "Cập nhật thông tin thành công");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }
}
