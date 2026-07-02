package com.multimodalAgent.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupportTaskRequest(
        @NotBlank @Size(max = 160) String title,
        @Size(max = 80) String category,
        @Size(max = 2000) String detail
) {
}
