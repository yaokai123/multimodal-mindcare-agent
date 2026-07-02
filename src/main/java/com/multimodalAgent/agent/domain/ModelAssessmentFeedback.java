package com.multimodalAgent.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "model_assessment_feedback")
public class ModelAssessmentFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long reportId;

    @Column(nullable = false, length = 40)
    private String feedbackType;

    @Column(length = 40)
    private String correctedRiskLevel;

    @Column(nullable = false, length = 120)
    private String actor = "admin";

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String note;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public Long getReportId() {
        return reportId;
    }

    public void setReportId(Long reportId) {
        this.reportId = reportId;
    }

    public String getFeedbackType() {
        return feedbackType;
    }

    public void setFeedbackType(String feedbackType) {
        this.feedbackType = feedbackType;
    }

    public String getCorrectedRiskLevel() {
        return correctedRiskLevel;
    }

    public void setCorrectedRiskLevel(String correctedRiskLevel) {
        this.correctedRiskLevel = correctedRiskLevel;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
