package com.multimodalAgent.agent.dto;

import com.multimodalAgent.agent.domain.SupportTask;
import java.time.Instant;

public record SupportTaskResponse(
        Long id,
        String title,
        String category,
        String detail,
        String recommendationReason,
        boolean completed,
        Instant createdAt,
        Instant completedAt
) {
    public static SupportTaskResponse from(SupportTask task) {
        return new SupportTaskResponse(
                task.getId(),
                task.getTitle(),
                task.getCategory(),
                task.getDetail(),
                task.getRecommendationReason(),
                task.isCompleted(),
                task.getCreatedAt(),
                task.getCompletedAt());
    }
}
