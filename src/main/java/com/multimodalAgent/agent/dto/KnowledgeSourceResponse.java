package com.multimodalAgent.agent.dto;

import java.time.Instant;

public record KnowledgeSourceResponse(
        String source,
        String category,
        String tags,
        String audience,
        String riskLevel,
        long chunks,
        Instant latestCreatedAt,
        boolean active,
        int version,
        String versionStatus,
        long feedbackCount
) {
}
