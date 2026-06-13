package com.example.manga_management.ai.dto;

import lombok.Data;

@Data
public class AIRequestDTO {

    /** Feature code – one of: color, shadow, clean_line, background, lettering, expression */
    private String feature;

    /** Optional additional instruction from the user */
    private String prompt;

    /** Base64-encoded image (required for vision features, optional for image generation) */
    private String imageBase64;
}
