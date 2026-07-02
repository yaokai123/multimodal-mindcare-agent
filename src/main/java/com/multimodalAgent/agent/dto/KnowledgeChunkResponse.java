package com.multimodalAgent.agent.dto;

import com.multimodalAgent.agent.domain.KnowledgeChunk;
import java.time.Instant;

public record KnowledgeChunkResponse(
        Long id,
        String source,
        String category,
        String tags,
        String audience,
        String riskLevel,
        int sourceIndex,
        String content,
        boolean active,
        int version,
        String versionStatus,
        String versionNote,
        Instant createdAt
) {
    public static KnowledgeChunkResponse from(KnowledgeChunk chunk) {
        return new KnowledgeChunkResponse(
                chunk.getId(),
                chunk.getSource(),
                chunk.getCategory(),
                chunk.getTags(),
                chunk.getAudience(),
                chunk.getRiskLevel(),
                chunk.getSourceIndex(),
                chunk.getContent(),
                chunk.isActive(),
                chunk.getVersion(),
                chunk.getVersionStatus(),
                chunk.getVersionNote(),
                chunk.getCreatedAt());
    }
}
