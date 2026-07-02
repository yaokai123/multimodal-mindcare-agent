package com.multimodalAgent.agent.dto;

import com.multimodalAgent.agent.domain.KnowledgeCitationFeedback;
import java.time.Instant;

public record KnowledgeCitationFeedbackResponse(
        Long id,
        Long chunkId,
        String source,
        String category,
        String actor,
        String reason,
        String note,
        Instant createdAt
) {
    public static KnowledgeCitationFeedbackResponse from(KnowledgeCitationFeedback feedback) {
        return new KnowledgeCitationFeedbackResponse(
                feedback.getId(),
                feedback.getChunkId(),
                feedback.getSource(),
                feedback.getCategory(),
                feedback.getActor(),
                feedback.getReason(),
                feedback.getNote(),
                feedback.getCreatedAt());
    }
}
