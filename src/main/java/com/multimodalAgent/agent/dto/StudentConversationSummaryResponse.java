package com.multimodalAgent.agent.dto;

import com.multimodalAgent.agent.domain.RiskLevel;
import java.time.Instant;

public record StudentConversationSummaryResponse(
        String sessionId,
        String title,
        String summary,
        RiskLevel riskLevel,
        Instant createdAt,
        Instant updatedAt,
        boolean hasAudio,
        boolean hasImage,
        boolean hasVideo
) {
}
