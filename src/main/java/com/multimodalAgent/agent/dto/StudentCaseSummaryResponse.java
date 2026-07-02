package com.multimodalAgent.agent.dto;

import java.time.Instant;

public record StudentCaseSummaryResponse(
        Long userId,
        String username,
        String displayName,
        String latestRiskLevel,
        double moodAverage30d,
        long openTickets,
        long activeGoals,
        long pendingTasks,
        Instant latestActivityAt,
        String nextStep
) {
}
