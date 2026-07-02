package com.multimodalAgent.agent.dto;

import java.util.List;

public record StudentProfileResponse(
        Long userId,
        String username,
        String displayName,
        String currentSupportFocus,
        List<WindowSummary> summaries,
        List<ConversationMessageResponse> recentConversation,
        SupportTrendResponse trend,
        List<SupportTaskResponse> tasks,
        List<RiskTicketResponse> riskTickets,
        List<StudentCaseNoteResponse> adminNotes,
        List<SupportGoalResponse> goals
) {
    public record WindowSummary(
            int days,
            double averageMood,
            int moodEntries,
            long completedTasks,
            long totalTasks,
            long openTickets,
            String latestRiskLevel,
            String careSuggestion
    ) {
    }
}
