package com.multimodalAgent.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VoiceTtsRequest(
        @NotBlank @Size(max = 180) String roomName,
        @Size(max = 80) String sessionId,
        @NotBlank @Size(max = 6000) String text,
        @Size(max = 120) String voice,
        @Size(max = 20) String format
) {
}
