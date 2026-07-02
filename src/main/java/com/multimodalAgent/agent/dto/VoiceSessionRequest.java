package com.multimodalAgent.agent.dto;

import jakarta.validation.constraints.Size;

public record VoiceSessionRequest(
        String sessionId,
        @Size(max = 120) String knowledgeScope,
        @Size(max = 120) String supportGoal
) {
}
