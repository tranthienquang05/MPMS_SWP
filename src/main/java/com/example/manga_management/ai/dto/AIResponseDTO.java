package com.example.manga_management.ai.dto;

public class AIResponseDTO {

    /** "success" or "error" */
    private final String status;

    /** "image" or "text" */
    private final String type;

    /** The AI result – base64 image for image type, analysis text for text type */
    private final String result;

    /** Error message (populated only when status is "error") */
    private final String message;

    /** x-request-id from the API response, useful for debugging */
    private final String requestId;

    private AIResponseDTO(Builder builder) {
        this.status = builder.status;
        this.type = builder.type;
        this.result = builder.result;
        this.message = builder.message;
        this.requestId = builder.requestId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getStatus() {
        return status;
    }

    public String getType() {
        return type;
    }

    public String getResult() {
        return result;
    }

    public String getMessage() {
        return message;
    }

    public String getRequestId() {
        return requestId;
    }

    public static class Builder {
        private String status;
        private String type;
        private String result;
        private String message;
        private String requestId;

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder result(String result) {
            this.result = result;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public AIResponseDTO build() {
            return new AIResponseDTO(this);
        }
    }
}
