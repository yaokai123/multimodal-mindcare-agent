package com.multimodalAgent.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeSearchRequest(
        @NotBlank @Size(max = 500) String query,
        @Size(max = 120) String scope,
        Double minScore
) {
}
