package com.multimodalAgent.agent.dto;

public record KnowledgeCitation(
        Long chunkId,
        String source,
        String category,
        String tags,
        String audience,
        String riskLevel,
        double score,
        String excerpt,
        boolean shown,
        String basis
) {
}
