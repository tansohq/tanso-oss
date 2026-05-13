package com.tansoflow.tansocore.application.listener;

import com.tansoflow.tansocore.entity.AuditLog;
import com.tansoflow.tansocore.model.event.audit.AuditEvent;
import com.tansoflow.tansocore.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class AuditEventListener {
    private final AuditLogRepository auditLogRepository;

    @Async
    @EventListener
    public void onAuditEvent(AuditEvent event) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setAction(event.action());
            auditLog.setActorUserId(event.actorUserId());
            auditLog.setActorAccountId(event.actorAccountId());
            auditLog.setTargetEntityType(event.targetEntityType());
            auditLog.setTargetEntityId(event.targetEntityId());
            auditLog.setDetail(event.detail());
            auditLog.setIpAddress(event.ipAddress());
            auditLog.setRequestId(event.requestId());

            auditLogRepository.save(auditLog);
            log.debug("Audit event persisted: action={}, actorUserId={}", event.action(), event.actorUserId());
        } catch (Exception e) {
            log.error("Failed to persist audit event: action={}, error={}", event.action(), e.getMessage(), e);
        }
    }
}
