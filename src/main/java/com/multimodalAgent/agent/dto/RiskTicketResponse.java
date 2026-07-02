package com.multimodalAgent.agent.dto;

import com.multimodalAgent.agent.domain.RiskTicket;
import com.multimodalAgent.agent.domain.RiskTicketStatus;
import java.time.Instant;
import java.util.List;

public record RiskTicketResponse(
        Long id,
        Long reportId,
        Long userId,
        String username,
        String sessionId,
        RiskTicketStatus status,
        String priority,
        String assignedTo,
        String lastActionBy,
        String handlerNote,
        String actionLog,
        String contactMethod,
        String contactTarget,
        Instant firstRespondedAt,
        Instant lastContactAt,
        String referredTo,
        String closureReason,
        String resolutionType,
        String resolutionReason,
        Instant slaDueAt,
        boolean slaBreached,
        long slaMinutesRemaining,
        int slaReminderStage,
        Instant escalatedAt,
        List<RiskTicketEventResponse> events,
        String riskLevel,
        String emotion,
        String summary,
        String content,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt
) {
    public static RiskTicketResponse from(RiskTicket ticket) {
        return from(ticket, List.of());
    }

    public static RiskTicketResponse from(RiskTicket ticket, List<RiskTicketEventResponse> events) {
        var report = ticket.getReport();
        Instant now = Instant.now();
        long minutesRemaining = ticket.getSlaDueAt() == null
                ? 0
                : java.time.Duration.between(now, ticket.getSlaDueAt()).toMinutes();
        boolean breached = ticket.getStatus().isActive()
                && ticket.getSlaDueAt() != null
                && ticket.getSlaDueAt().isBefore(now);
        return new RiskTicketResponse(
                ticket.getId(),
                report.getId(),
                report.getUser().getId(),
                report.getUser().getUsername(),
                report.getSession() == null ? null : report.getSession().getPublicId(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getAssignedTo(),
                ticket.getLastActionBy(),
                ticket.getHandlerNote(),
                ticket.getActionLog(),
                ticket.getContactMethod(),
                ticket.getContactTarget(),
                ticket.getFirstRespondedAt(),
                ticket.getLastContactAt(),
                ticket.getReferredTo(),
                ticket.getClosureReason(),
                ticket.getResolutionType(),
                ticket.getResolutionReason(),
                ticket.getSlaDueAt(),
                breached,
                minutesRemaining,
                ticket.getSlaReminderStage(),
                ticket.getEscalatedAt(),
                events,
                report.getRiskLevel().name(),
                report.getEmotion().name(),
                report.getSummary(),
                report.getContent(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getClosedAt());
    }
}
