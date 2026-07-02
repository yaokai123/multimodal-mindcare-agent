package com.multimodalAgent.agent.dto;

public record TriggerClusterResponse(
        String trigger,
        long count,
        double averageScore
) {
}
