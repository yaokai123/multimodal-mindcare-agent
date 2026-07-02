package com.multimodalAgent.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "risk_tickets")
public class RiskTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false, unique = true)
    private PsychologicalReport report;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private RiskTicketStatus status = RiskTicketStatus.PENDING;

    @Column(nullable = false, length = 20)
    private String priority = "HIGH";

    @Column(length = 120)
    private String assignedTo;

    @Column(length = 120)
    private String lastActionBy;

    @Lob
    private String handlerNote;

    @Lob
    private String actionLog;

    @Column(length = 40)
    private String contactMethod;

    @Column(length = 160)
    private String contactTarget;

    private Instant firstRespondedAt;

    private Instant lastContactAt;

    @Column(length = 160)
    private String referredTo;

    @Column(length = 80)
    private String closureReason;

    @Column(length = 80)
    private String resolutionType;

    @Lob
    private String resolutionReason;

    @Column(nullable = false)
    private Instant slaDueAt = Instant.now().plusSeconds(4 * 60 * 60);

    @Column(nullable = false)
    private int slaReminderStage = 0;

    private Instant escalatedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    private Instant closedAt;

    public Long getId() {
        return id;
    }

    public PsychologicalReport getReport() {
        return report;
    }

    public void setReport(PsychologicalReport report) {
        this.report = report;
    }

    public RiskTicketStatus getStatus() {
        return status;
    }

    public void setStatus(RiskTicketStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
        if (status == RiskTicketStatus.CLOSED || status == RiskTicketStatus.RESOLVED) {
            this.closedAt = Instant.now();
        } else {
            this.closedAt = null;
        }
        if (status == RiskTicketStatus.ESCALATED && this.escalatedAt == null) {
            this.escalatedAt = Instant.now();
        }
        if ((status == RiskTicketStatus.IN_PROGRESS || status == RiskTicketStatus.ACKNOWLEDGED)
                && this.firstRespondedAt == null) {
            this.firstRespondedAt = Instant.now();
        }
        if ((status == RiskTicketStatus.CONTACTED || status == RiskTicketStatus.UNREACHABLE)
                && this.lastContactAt == null) {
            this.lastContactAt = Instant.now();
        }
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(String assignedTo) {
        this.assignedTo = assignedTo;
        this.updatedAt = Instant.now();
    }

    public String getLastActionBy() {
        return lastActionBy;
    }

    public void setLastActionBy(String lastActionBy) {
        this.lastActionBy = lastActionBy;
        this.updatedAt = Instant.now();
    }

    public String getHandlerNote() {
        return handlerNote;
    }

    public void setHandlerNote(String handlerNote) {
        this.handlerNote = handlerNote;
        this.updatedAt = Instant.now();
    }

    public String getActionLog() {
        return actionLog;
    }

    public void appendAction(String action) {
        if (action == null || action.isBlank()) {
            return;
        }
        String next = Instant.now() + " " + action.trim();
        this.actionLog = this.actionLog == null || this.actionLog.isBlank()
                ? next
                : this.actionLog + "\n" + next;
        this.updatedAt = Instant.now();
    }

    public String getContactMethod() {
        return contactMethod;
    }

    public void setContactMethod(String contactMethod) {
        this.contactMethod = contactMethod;
        this.updatedAt = Instant.now();
    }

    public String getContactTarget() {
        return contactTarget;
    }

    public void setContactTarget(String contactTarget) {
        this.contactTarget = contactTarget;
        this.updatedAt = Instant.now();
    }

    public Instant getFirstRespondedAt() {
        return firstRespondedAt;
    }

    public Instant getLastContactAt() {
        return lastContactAt;
    }

    public void markContacted() {
        this.lastContactAt = Instant.now();
        if (this.firstRespondedAt == null) {
            this.firstRespondedAt = this.lastContactAt;
        }
        this.updatedAt = Instant.now();
    }

    public String getReferredTo() {
        return referredTo;
    }

    public void setReferredTo(String referredTo) {
        this.referredTo = referredTo;
        this.updatedAt = Instant.now();
    }

    public String getClosureReason() {
        return closureReason;
    }

    public void setClosureReason(String closureReason) {
        this.closureReason = closureReason;
        this.updatedAt = Instant.now();
    }

    public String getResolutionType() {
        return resolutionType;
    }

    public void setResolutionType(String resolutionType) {
        this.resolutionType = resolutionType;
        this.updatedAt = Instant.now();
    }

    public String getResolutionReason() {
        return resolutionReason;
    }

    public void setResolutionReason(String resolutionReason) {
        this.resolutionReason = resolutionReason;
        this.updatedAt = Instant.now();
    }

    public Instant getSlaDueAt() {
        return slaDueAt;
    }

    public void setSlaDueAt(Instant slaDueAt) {
        this.slaDueAt = slaDueAt;
    }

    public int getSlaReminderStage() {
        return slaReminderStage;
    }

    public void setSlaReminderStage(int slaReminderStage) {
        this.slaReminderStage = slaReminderStage;
        this.updatedAt = Instant.now();
    }

    public Instant getEscalatedAt() {
        return escalatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
