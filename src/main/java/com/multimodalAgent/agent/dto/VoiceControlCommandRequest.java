package com.multimodalAgent.agent.dto;

import jakarta.validation.constraints.Size;

public record VoiceControlCommandRequest(
        @Size(max = 60) String command,
        @Size(max = 500) String reason
) {
}
