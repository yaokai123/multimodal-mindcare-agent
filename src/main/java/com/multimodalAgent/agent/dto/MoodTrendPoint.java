package com.multimodalAgent.agent.dto;

import java.time.LocalDate;

public record MoodTrendPoint(
        LocalDate date,
        double averageScore,
        int entries
) {
}
