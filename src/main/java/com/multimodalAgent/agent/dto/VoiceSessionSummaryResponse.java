package com.multimodalAgent.agent.dto;

import com.multimodalAgent.agent.domain.RiskLevel;
import java.time.Instant;

public record VoiceSessionSummaryResponse(
        String roomName,
        String sessionId,
        Instant startedAt,
        Instant endedAt,
        RiskLevel riskBefore,
        RiskLevel riskAfter,
        boolean riskChanged,
        String supportMode,
        String ttsTone,
        String summary,
        String suggestedFollowUp
) {
}
