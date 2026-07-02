package com.multimodalAgent.agent.dto;

import com.multimodalAgent.agent.domain.RiskTicketEvent;
import java.time.Instant;

public record RiskTicketEventResponse(
        Long id,
        String eventType,
        String outcome,
        String actor,
        String contactMethod,
        String contactTarget,
        String note,
        Instant createdAt
) {
    public static RiskTicketEventResponse from(RiskTicketEvent event) {
        return new RiskTicketEventResponse(
                event.getId(),
                event.getEventType(),
                event.getOutcome(),
                event.getActor(),
                event.getContactMethod(),
                event.getContactTarget(),
                event.getNote(),
                event.getCreatedAt());
    }
}
