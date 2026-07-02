package com.multimodalAgent.agent.dto;

import com.multimodalAgent.agent.domain.StudentCaseNote;
import java.time.Instant;

public record StudentCaseNoteResponse(
        Long id,
        String actor,
        String noteType,
        String content,
        Instant createdAt
) {
    public static StudentCaseNoteResponse from(StudentCaseNote note) {
        return new StudentCaseNoteResponse(
                note.getId(),
                note.getActor(),
                note.getNoteType(),
                note.getContent(),
                note.getCreatedAt());
    }
}
