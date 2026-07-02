package com.multimodalAgent.agent.dto;

import java.util.List;

public record SupportTrendResponse(
        int days,
        List<MoodTrendPoint> moodTrend,
        List<TriggerClusterResponse> triggerClusters,
        int consecutiveLowMoodEntries,
        boolean lowMoodAlert,
        String latestRiskLevel,
        String careSuggestion,
        long totalTasks,
        long completedTasks,
        double taskCompletionRate
) {
}
