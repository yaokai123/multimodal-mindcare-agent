package com.multimodalAgent.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ModelAssessmentFeedbackRequest(
        @NotBlank @Size(max = 40) String feedbackType,
        @Size(max = 40) String correctedRiskLevel,
        @Size(max = 1000) String note
) {
}
