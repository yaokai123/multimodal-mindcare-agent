package com.multimodalAgent.agent.controller;

import com.multimodalAgent.agent.dto.RiskTicketEventRequest;
import com.multimodalAgent.agent.dto.RiskTicketEventResponse;
import com.multimodalAgent.agent.dto.RiskTicketResponse;
import com.multimodalAgent.agent.dto.RiskTicketUpdateRequest;
import com.multimodalAgent.agent.security.CurrentUser;
import com.multimodalAgent.agent.service.AuditService;
import com.multimodalAgent.agent.service.RiskTicketService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/risk-tickets")
public class RiskTicketController {

    private final RiskTicketService riskTicketService;
    private final AuditService auditService;

    public RiskTicketController(RiskTicketService riskTicketService, AuditService auditService) {
        this.riskTicketService = riskTicketService;
        this.auditService = auditService;
    }

    @GetMapping
    public List<RiskTicketResponse> latestTickets() {
        return riskTicketService.latestTickets().stream()
                .map(ticket -> RiskTicketResponse.from(ticket, riskTicketService.ticketEvents(ticket.getId()).stream()
                        .map(RiskTicketEventResponse::from)
                        .toList()))
                .toList();
    }

    @PatchMapping("/{id}")
    public RiskTicketResponse updateTicket(
            @PathVariable Long id,
            @Valid @RequestBody RiskTicketUpdateRequest request,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        auditService.log(actor(currentUser), "RISK_TICKET_UPDATE", "RISK_TICKET", String.valueOf(id),
                "status=" + request.status() + ", assignedTo=" + request.assignedTo());
        var updated = riskTicketService.updateTicket(id, request, actor(currentUser));
        return RiskTicketResponse.from(updated, riskTicketService.ticketEvents(updated.getId()).stream()
                .map(RiskTicketEventResponse::from)
                .toList());
    }

    @GetMapping("/{id}/events")
    public List<RiskTicketEventResponse> ticketEvents(@PathVariable Long id) {
        return riskTicketService.ticketEvents(id).stream()
                .map(RiskTicketEventResponse::from)
                .toList();
    }

    @PostMapping("/{id}/events")
    public RiskTicketEventResponse addEvent(
            @PathVariable Long id,
            @Valid @RequestBody RiskTicketEventRequest request,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        auditService.log(actor(currentUser), "RISK_TICKET_EVENT", "RISK_TICKET", String.valueOf(id),
                request.eventType() + " / " + request.outcome());
        return RiskTicketEventResponse.from(riskTicketService.addEvent(id, request, actor(currentUser)));
    }

    private String actor(CurrentUser currentUser) {
        return currentUser.getDisplayName() == null || currentUser.getDisplayName().isBlank()
                ? currentUser.getUsername()
                : currentUser.getDisplayName();
    }
}
