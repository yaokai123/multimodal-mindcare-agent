package com.multimodalAgent.agent.dto;

import java.time.Instant;

public record StudentAnomalyResponse(
        Long userId,
        String username,
        String riskLevel,
        double moodAverage,
        long openTickets,
        String reason,
        Instant latestActivityAt
) {
}
