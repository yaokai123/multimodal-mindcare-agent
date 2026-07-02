package com.multimodalAgent.agent.service;

import com.multimodalAgent.agent.domain.PsychologicalReport;
import com.multimodalAgent.agent.domain.RiskLevel;
import com.multimodalAgent.agent.domain.RiskTicket;
import com.multimodalAgent.agent.domain.RiskTicketEvent;
import com.multimodalAgent.agent.domain.RiskTicketStatus;
import com.multimodalAgent.agent.dto.RiskTicketEventRequest;
import com.multimodalAgent.agent.dto.RiskTicketUpdateRequest;
import com.multimodalAgent.agent.repository.PsychologicalReportRepository;
import com.multimodalAgent.agent.repository.RiskTicketEventRepository;
import com.multimodalAgent.agent.repository.RiskTicketRepository;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskTicketService {

    private final RiskTicketRepository riskTicketRepository;
    private final PsychologicalReportRepository reportRepository;
    private final RiskTicketEventRepository eventRepository;
    private final AuditService auditService;

    public RiskTicketService(
            RiskTicketRepository riskTicketRepository,
            PsychologicalReportRepository reportRepository,
            RiskTicketEventRepository eventRepository,
            AuditService auditService
    ) {
        this.riskTicketRepository = riskTicketRepository;
        this.reportRepository = reportRepository;
        this.eventRepository = eventRepository;
        this.auditService = auditService;
    }

    @Transactional
    public void ensureTicketForReport(PsychologicalReport report) {
        if (report.getRiskLevel() != RiskLevel.HIGH) {
            return;
        }
        riskTicketRepository.findByReport_Id(report.getId())
                .orElseGet(() -> {
                    RiskTicket ticket = new RiskTicket();
                    ticket.setReport(report);
                    ticket.setSlaDueAt(Instant.now().plusSeconds(30 * 60));
                    ticket.appendAction("created automatically from high-risk report #" + report.getId());
                    RiskTicket saved = riskTicketRepository.save(ticket);
                    appendEvent(saved, "CREATED", "HIGH_RISK_REPORT", "system", "Created from high-risk report #" + report.getId());
                    auditService.log("system", "RISK_TICKET_CREATED", "RISK_TICKET", String.valueOf(saved.getId()),
                            "High-risk report #" + report.getId() + " opened a crisis intervention ticket.");
                    return saved;
                });
    }

    @Transactional
    public List<RiskTicket> latestTickets() {
        List<RiskTicket> tickets = riskTicketRepository.findTop100ByOrderByUpdatedAtDesc();
        tickets.forEach(this::autoEscalateIfSlaBreached);
        return tickets;
    }

    @Transactional
    public void backfillHighRiskTickets() {
        reportRepository.findTop200ByRiskLevelOrderByCreatedAtDesc(RiskLevel.HIGH)
                .forEach(this::ensureTicketForReport);
    }

    @Transactional
    public RiskTicket updateTicket(Long id, RiskTicketUpdateRequest request, String actor) {
        RiskTicket ticket = riskTicketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Risk ticket not found: " + id));
        RiskTicketStatus previous = ticket.getStatus();
        if (request.status() != null && request.status() != previous) {
            validateTransition(ticket, request);
            ticket.setStatus(request.status());
            ticket.appendAction(actor + " changed status from " + previous + " to " + request.status());
            appendEvent(ticket, "STATUS_CHANGED", previous + "_TO_" + request.status(), actor, statusNote(request.status()));
        }
        if (request.assignedTo() != null) {
            ticket.setAssignedTo(blankToNull(request.assignedTo()));
            ticket.appendAction(actor + " assigned ticket to " + valueOrUnassigned(ticket.getAssignedTo()));
            appendEvent(ticket, "ASSIGNMENT", valueOrUnassigned(ticket.getAssignedTo()), actor, null);
        }
        if (request.handlerNote() != null) {
            ticket.setHandlerNote(blankToNull(request.handlerNote()));
            ticket.appendAction(actor + " added handler note");
            appendEvent(ticket, "NOTE", null, actor, request.handlerNote());
        }
        if (request.contactMethod() != null || request.contactTarget() != null) {
            ticket.setContactMethod(normalizeToken(request.contactMethod(), 40));
            ticket.setContactTarget(blankToNull(request.contactTarget()));
            ticket.markContacted();
            appendEvent(ticket, "CONTACT", ticket.getStatus().name(), actor, ticket.getContactMethod(), ticket.getContactTarget(),
                    "Contact record updated from ticket editor.");
        }
        if (request.referredTo() != null) {
            ticket.setReferredTo(blankToNull(request.referredTo()));
            appendEvent(ticket, "REFERRAL", valueOrUnassigned(ticket.getReferredTo()), actor,
                    "Referral target updated.");
        }
        if (request.closureReason() != null) {
            ticket.setClosureReason(normalizeToken(request.closureReason(), 80));
        }
        if (request.resolutionType() != null) {
            ticket.setResolutionType(normalizeToken(request.resolutionType(), 80));
        }
        if (request.resolutionReason() != null) {
            ticket.setResolutionReason(blankToNull(request.resolutionReason()));
        }
        ticket.setLastActionBy(actor);
        RiskTicket saved = riskTicketRepository.save(ticket);
        auditService.log(actor, "RISK_TICKET_STATE_CHANGE", "RISK_TICKET", String.valueOf(saved.getId()),
                "status=" + previous + "->" + saved.getStatus()
                        + ", contactMethod=" + valueOrUnassigned(saved.getContactMethod())
                        + ", closureReason=" + valueOrUnassigned(saved.getClosureReason()));
        if (saved.getStatus().isClosed()
                && (request.resolutionType() != null || request.resolutionReason() != null)) {
            appendEvent(saved, "RESOLUTION", saved.getResolutionType(), actor, saved.getResolutionReason());
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<RiskTicketEvent> ticketEvents(Long ticketId) {
        return eventRepository.findByTicket_IdOrderByCreatedAtDesc(ticketId);
    }

    @Transactional
    public RiskTicketEvent addEvent(Long ticketId, RiskTicketEventRequest request, String actor) {
        RiskTicket ticket = riskTicketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Risk ticket not found: " + ticketId));
        String type = normalizeToken(request.eventType(), 40);
        String outcome = normalizeToken(request.outcome(), 120);
        String contactMethod = normalizeToken(request.contactMethod(), 40);
        String contactTarget = blankToNull(request.contactTarget());
        RiskTicketEvent event = appendEvent(ticket, type, outcome, actor, contactMethod, contactTarget, request.note());
        applyEventStatus(ticket, type, outcome, contactMethod, contactTarget);
        ticket.setLastActionBy(actor);
        riskTicketRepository.save(ticket);
        auditService.log(actor, "RISK_TICKET_TIMELINE_EVENT", "RISK_TICKET", String.valueOf(ticketId),
                type + " / " + outcome + " / method=" + valueOrUnassigned(contactMethod));
        return event;
    }

    private RiskTicketEvent appendEvent(RiskTicket ticket, String eventType, String outcome, String actor, String note) {
        return appendEvent(ticket, eventType, outcome, actor, null, null, note);
    }

    private RiskTicketEvent appendEvent(
            RiskTicket ticket,
            String eventType,
            String outcome,
            String actor,
            String contactMethod,
            String contactTarget,
            String note
    ) {
        RiskTicketEvent event = new RiskTicketEvent();
        event.setTicket(ticket);
        event.setEventType(eventType == null || eventType.isBlank() ? "NOTE" : eventType);
        event.setOutcome(blankToNull(outcome));
        event.setActor(blankToNull(actor));
        event.setContactMethod(blankToNull(contactMethod));
        event.setContactTarget(blankToNull(contactTarget));
        event.setNote(blankToNull(note));
        return eventRepository.save(event);
    }

    private void applyEventStatus(RiskTicket ticket, String type, String outcome, String contactMethod, String contactTarget) {
        if ("CONTACT".equals(type)) {
            ticket.setContactMethod(contactMethod);
            ticket.setContactTarget(contactTarget);
            ticket.markContacted();
            ticket.setStatus("UNREACHABLE".equals(outcome) ? RiskTicketStatus.UNREACHABLE : RiskTicketStatus.CONTACTED);
        } else if ("ESCALATION".equals(type) || "REFERRED".equals(outcome)) {
            ticket.setStatus(RiskTicketStatus.ESCALATED);
        } else if ("REFERRAL".equals(type)) {
            ticket.setStatus(RiskTicketStatus.REFERRED);
            ticket.setReferredTo(outcome);
        } else if ("RESOLUTION".equals(type)) {
            ticket.setStatus(RiskTicketStatus.CLOSED);
            ticket.setResolutionType(outcome);
        }
        ticket.appendAction(type + (outcome == null ? "" : " / " + outcome));
    }

    private void autoEscalateIfSlaBreached(RiskTicket ticket) {
        emitSlaReminderIfNeeded(ticket);
        if (ticket.getStatus().isClosed()
                || ticket.getStatus() == RiskTicketStatus.ESCALATED
                || ticket.getSlaDueAt() == null
                || !ticket.getSlaDueAt().isBefore(Instant.now())) {
            return;
        }
        ticket.setStatus(RiskTicketStatus.ESCALATED);
        ticket.appendAction("system escalated ticket because SLA was breached");
        riskTicketRepository.save(ticket);
        appendEvent(ticket, "ESCALATION", "SLA_BREACHED", "system", "SLA due time passed without resolution.");
        auditService.log("system", "RISK_TICKET_SLA_ESCALATED", "RISK_TICKET", String.valueOf(ticket.getId()),
                "SLA breached for high-risk ticket.");
    }

    private void emitSlaReminderIfNeeded(RiskTicket ticket) {
        if (ticket.getStatus().isClosed() || ticket.getCreatedAt() == null) {
            return;
        }
        long elapsedMinutes = Duration.between(ticket.getCreatedAt(), Instant.now()).toMinutes();
        int nextStage = ticket.getSlaReminderStage();
        if (elapsedMinutes >= 30 && nextStage < 30) {
            nextStage = 30;
        } else if (elapsedMinutes >= 10 && nextStage < 10) {
            nextStage = 10;
        } else if (elapsedMinutes >= 5 && nextStage < 5) {
            nextStage = 5;
        }
        if (nextStage == ticket.getSlaReminderStage()) {
            return;
        }
        ticket.setSlaReminderStage(nextStage);
        ticket.appendAction("system emitted SLA " + nextStage + " minute reminder");
        riskTicketRepository.save(ticket);
        appendEvent(ticket, "SLA_REMINDER", "SLA_" + nextStage + "_MIN", "system",
                "High-risk ticket has not been closed after " + nextStage + " minutes.");
        auditService.log("system", "RISK_TICKET_SLA_REMINDER", "RISK_TICKET", String.valueOf(ticket.getId()),
                "stage=" + nextStage);
    }

    private void validateTransition(RiskTicket ticket, RiskTicketUpdateRequest request) {
        RiskTicketStatus status = request.status();
        if (status != null && status.isClosed()) {
            boolean hasClosureReason = notBlank(request.closureReason()) || notBlank(ticket.getClosureReason());
            boolean hasResolution = notBlank(request.resolutionType()) || notBlank(ticket.getResolutionType())
                    || notBlank(request.resolutionReason()) || notBlank(ticket.getResolutionReason());
            if (!hasClosureReason && !hasResolution) {
                throw new IllegalArgumentException("Closing a risk ticket requires a closure reason or resolution note.");
            }
        }
    }

    private String statusNote(RiskTicketStatus status) {
        return switch (status) {
            case PENDING, OPEN -> "Ticket is waiting for a human responder.";
            case IN_PROGRESS, ACKNOWLEDGED -> "Responder has acknowledged and started handling the ticket.";
            case CONTACTED -> "Student or emergency contact has been reached.";
            case UNREACHABLE -> "Contact attempt failed and follow-up is required.";
            case REFERRED -> "Case was referred to another support channel.";
            case ESCALATED -> "Case was escalated for urgent handling.";
            case CLOSED, RESOLVED -> "Case was closed with a documented reason.";
        };
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeToken(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_");
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }

    private String valueOrUnassigned(String value) {
        return value == null || value.isBlank() ? "unassigned" : value;
    }
}
