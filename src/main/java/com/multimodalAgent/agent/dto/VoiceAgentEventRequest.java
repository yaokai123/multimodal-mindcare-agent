package com.multimodalAgent.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VoiceAgentEventRequest(
        @NotBlank @Size(max = 180) String roomName,
        @Size(max = 80) String sessionId,
        @NotBlank @Size(max = 60) String type,
        @Size(max = 60) String phase,
        @Size(max = 2000) String text
) {
}
