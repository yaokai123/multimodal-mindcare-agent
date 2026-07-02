package com.multimodalAgent.agent.dto;

import com.multimodalAgent.agent.domain.ModelAssessmentFeedback;
import java.time.Instant;

public record ModelAssessmentFeedbackResponse(
        Long id,
        Long reportId,
        String feedbackType,
        String correctedRiskLevel,
        String actor,
        String note,
        Instant createdAt
) {
    public static ModelAssessmentFeedbackResponse from(ModelAssessmentFeedback feedback) {
        return new ModelAssessmentFeedbackResponse(
                feedback.getId(),
                feedback.getReportId(),
                feedback.getFeedbackType(),
                feedback.getCorrectedRiskLevel(),
                feedback.getActor(),
                feedback.getNote(),
                feedback.getCreatedAt());
    }
}
