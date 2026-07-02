package com.multimodalAgent.agent.dto;

import com.multimodalAgent.agent.domain.AuditLog;
import java.time.Instant;

public record AuditLogResponse(
        Long id,
        String actor,
        String action,
        String targetType,
        String targetId,
        String detail,
        Instant createdAt
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getActor(),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getDetail(),
                log.getCreatedAt());
    }
}
