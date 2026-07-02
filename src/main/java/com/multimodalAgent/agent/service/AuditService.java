package com.multimodalAgent.agent.service;

import com.multimodalAgent.agent.domain.AuditLog;
import com.multimodalAgent.agent.repository.AuditLogRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void log(String actor, String action, String targetType, String targetId, String detail) {
        AuditLog log = new AuditLog();
        log.setActor(actor == null || actor.isBlank() ? "system" : actor);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detail);
        auditLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> latest() {
        return auditLogRepository.findTop100ByOrderByCreatedAtDesc();
    }
}
