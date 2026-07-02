package com.multimodalAgent.agent.dto;

import jakarta.validation.constraints.Size;

public record KnowledgeUpdateRequest(
        @Size(max = 80) String category,
        @Size(max = 240) String tags,
        @Size(max = 120) String audience,
        @Size(max = 40) String riskLevel,
        Boolean active,
        @Size(max = 80) String versionStatus,
        @Size(max = 500) String versionNote
) {
}
