package com.example.manga_management.ai.enums;

import java.util.Arrays;

public enum AIFeature {

    COLOR("color", "image",
            "Tô màu panel manga này theo gam màu tự nhiên, giữ nguyên lineart"),

    SHADOW("shadow", "image",
            "Thêm shading và đổ bóng cho panel manga này, ánh sáng từ trên xuống"),

    CLEAN_LINE("clean_line", "image",
            "Clean up lineart của panel manga này, nét sạch sắc nét kiểu manga chuyên nghiệp"),

    BACKGROUND("background", "image",
            "Vẽ background chi tiết cho panel manga này"),

    LETTERING("lettering", "vision",
            "Phân tích balloon trong panel này, gợi ý căn chỉnh và font chữ phù hợp"),

    EXPRESSION("expression", "vision",
            "Nhận diện biểu cảm nhân vật trong panel, mô tả emotion và gợi ý cải thiện");

    private final String code;
    private final String type;
    private final String basePrompt;

    AIFeature(String code, String type, String basePrompt) {
        this.code = code;
        this.type = type;
        this.basePrompt = basePrompt;
    }

    public String getCode() {
        return code;
    }

    public String getType() {
        return type;
    }

    public String getBasePrompt() {
        return basePrompt;
    }

    /**
     * Resolve an AIFeature from its code string.
     *
     * @param code the feature code (e.g. "color", "shadow")
     * @return the matching AIFeature
     * @throws IllegalArgumentException if no feature matches the given code
     */
    public static AIFeature fromCode(String code) {
        return Arrays.stream(values())
                .filter(f -> f.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown AI feature code: " + code));
    }
}
