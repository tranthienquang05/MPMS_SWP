        package com.example.manga_management.ai.enums;

        import java.util.Arrays;

        public enum AIFeature {

        COLOR("color", "image",
                "Color this manga panel with natural tones, preserve the original lineart exactly"),

        SHADOW("shadow", "image",
                "Add professional shading and drop shadows to this manga panel, light source from top"),

        CLEAN_LINE("clean_line", "image",
                "Clean up and sharpen the lineart of this manga panel in professional manga style"),

        BACKGROUND("background", "image",
                "Draw a detailed manga-style background for this panel"),

        LETTERING("lettering", "vision",
                "Analyze speech bubbles in this manga panel, suggest alignment and suitable font styles"),

        EXPRESSION("expression", "vision",
                "Identify character expressions in this panel, describe emotions and suggest improvements");

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
