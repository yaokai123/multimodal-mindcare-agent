package com.multimodalAgent.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VoiceAsrRequest(
        @NotBlank @Size(max = 180) String roomName,
        @Size(max = 80) String sessionId,
        @NotBlank @Size(max = 10_000_000) String audioBase64,
        @Size(max = 20) String format,
        Integer sampleRate,
        @Size(max = 20) String language,
        boolean finalTranscript
) {
}
