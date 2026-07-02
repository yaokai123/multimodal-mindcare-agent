package com.multimodalAgent.agent.dto;

import com.multimodalAgent.agent.domain.SupportGoal;
import java.time.Instant;

public record SupportGoalResponse(
        Long id,
        String title,
        String category,
        String detail,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static SupportGoalResponse from(SupportGoal goal) {
        return new SupportGoalResponse(
                goal.getId(),
                goal.getTitle(),
                goal.getCategory(),
                goal.getDetail(),
                goal.isActive(),
                goal.getCreatedAt(),
                goal.getUpdatedAt());
    }
}
