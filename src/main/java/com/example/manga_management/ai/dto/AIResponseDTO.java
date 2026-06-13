package com.example.manga_management.ai.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AIResponseDTO {

    /** "success" or "error" */
    private String status;

    /** "image" or "text" */
    private String type;

    /** The AI result – base64 image for image type, analysis text for text type */
    private String result;

    /** Error message (populated only when status is "error") */
    private String message;

    /** x-request-id from the API response, useful for debugging */
    private String requestId;
}
