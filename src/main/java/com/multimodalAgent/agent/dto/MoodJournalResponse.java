package com.multimodalAgent.agent.dto;

import com.multimodalAgent.agent.domain.MoodJournalEntry;
import java.time.Instant;

public record MoodJournalResponse(
        Long id,
        int moodScore,
        String moodLabel,
        String note,
        String trigger,
        Instant createdAt
) {
    public static MoodJournalResponse from(MoodJournalEntry entry) {
        return new MoodJournalResponse(
                entry.getId(),
                entry.getMoodScore(),
                entry.getMoodLabel(),
                entry.getNote(),
                entry.getTrigger(),
                entry.getCreatedAt());
    }
}
