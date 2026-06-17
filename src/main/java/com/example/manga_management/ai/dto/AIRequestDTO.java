package com.example.manga_management.ai.dto;

public class AIRequestDTO {

    /** Feature code – one of: color, shadow, clean_line, background, lettering, expression */
    private String feature;

    /** Optional additional instruction from the user */
    private String prompt;

    /** Base64-encoded image (required for vision features, optional for image generation) */
    private String imageBase64;

    /** Base64-encoded mask PNG (transparent area = region AI should edit, opaque = keep) */
    private String maskBase64;

    public String getFeature() {
        return feature;
    }

    public void setFeature(String feature) {
        this.feature = feature;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getImageBase64() {
        return imageBase64;
    }

    public void setImageBase64(String imageBase64) {
        this.imageBase64 = imageBase64;
    }

    public String getMaskBase64() {
        return maskBase64;
    }

    public void setMaskBase64(String maskBase64) {
        this.maskBase64 = maskBase64;
    }
}
