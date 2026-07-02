package com.multimodalAgent.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeCitationFeedbackRequest(
        Long chunkId,
        @NotBlank @Size(max = 180) String source,
        @Size(max = 80) String category,
        @Size(max = 80) String reason,
        @Size(max = 1000) String note
) {
}
