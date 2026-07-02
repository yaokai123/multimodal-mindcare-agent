package com.multimodalAgent.agent.dto;

import jakarta.validation.constraints.Size;

public record RiskTicketEventRequest(
        @Size(max = 40) String eventType,
        @Size(max = 120) String outcome,
        @Size(max = 40) String contactMethod,
        @Size(max = 160) String contactTarget,
        @Size(max = 2000) String note
) {
    public RiskTicketEventRequest(String eventType, String outcome, String note) {
        this(eventType, outcome, null, null, note);
    }
}
