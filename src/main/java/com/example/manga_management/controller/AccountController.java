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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
