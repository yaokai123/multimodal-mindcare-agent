package com.multimodalAgent.agent.dto;

import java.time.LocalDate;
import java.util.List;

public record AdminDashboardResponse(
        List<DailyRiskPoint> highRiskTrend,
        List<DailyTicketPoint> openTicketTrend,
        double averageResponseMinutes,
        List<NamedCount> riskSources,
        List<StudentAnomalyResponse> anomalies
) {
    public record DailyRiskPoint(LocalDate date, long highRiskReports) {
    }

    public record DailyTicketPoint(LocalDate date, long openTickets) {
    }

    public record NamedCount(String name, long count) {
    }
}
