package com.example.manga_management.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Kết quả trả về chung của API")
public class ApiResult {

    @Schema(description = "Trạng thái: success hoặc error", example = "success")
    private String status;

    @Schema(description = "Thông báo kết quả", example = "Đã gửi OTP tới email của bạn")
    private String message;
}
