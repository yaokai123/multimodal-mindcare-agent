package com.multimodalAgent.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VoiceAgentTranscriptRequest(
        @NotBlank @Size(max = 180) String roomName,
        @Size(max = 80) String sessionId,
        @NotBlank @Size(max = 4000) String text,
        boolean finalTranscript
) {
}
