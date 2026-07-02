package com.multimodalAgent.agent.dto;

import com.multimodalAgent.agent.domain.RiskTicketStatus;
import jakarta.validation.constraints.Size;

public record RiskTicketUpdateRequest(
        RiskTicketStatus status,
        @Size(max = 120) String assignedTo,
        @Size(max = 2000) String handlerNote,
        @Size(max = 40) String contactMethod,
        @Size(max = 160) String contactTarget,
        @Size(max = 160) String referredTo,
        @Size(max = 80) String closureReason,
        @Size(max = 80) String resolutionType,
        @Size(max = 2000) String resolutionReason
) {
    public RiskTicketUpdateRequest(
            RiskTicketStatus status,
            String assignedTo,
            String handlerNote,
            String resolutionType,
            String resolutionReason
    ) {
        this(status, assignedTo, handlerNote, null, null, null, null, resolutionType, resolutionReason);
    }
}
