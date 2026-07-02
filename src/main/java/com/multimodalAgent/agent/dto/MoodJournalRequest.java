package com.multimodalAgent.agent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MoodJournalRequest(
        @Min(1) @Max(5) int moodScore,
        @NotBlank @Size(max = 80) String moodLabel,
        @Size(max = 2000) String note,
        @Size(max = 120) String trigger
) {
}
