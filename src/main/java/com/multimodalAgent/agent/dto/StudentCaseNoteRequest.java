package com.multimodalAgent.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StudentCaseNoteRequest(
        @Size(max = 80) String noteType,
        @NotBlank @Size(max = 3000) String content
) {
}
