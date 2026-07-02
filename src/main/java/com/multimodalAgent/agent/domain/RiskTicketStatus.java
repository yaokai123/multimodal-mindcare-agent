package com.multimodalAgent.agent.domain;

public enum RiskTicketStatus {
    PENDING,
    IN_PROGRESS,
    UNREACHABLE,
    REFERRED,
    CLOSED,

    /**
     * Legacy values kept so existing databases can still boot and old tests keep their meaning.
     */
    OPEN,
    ACKNOWLEDGED,
    CONTACTED,
    ESCALATED,
    RESOLVED;

    public boolean isClosed() {
        return this == CLOSED || this == RESOLVED;
    }

    public boolean isActive() {
        return !isClosed();
    }
}
