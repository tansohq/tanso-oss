package com.tansoflow.tansocore.service.internal.audit;

import com.tansoflow.tansocore.model.event.audit.AuditEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AuditHelper {
    private final ApplicationEventPublisher eventPublisher;

    public void audit(String action, UUID actorUserId, UUID actorAccountId,
                      String targetEntityType, String targetEntityId, String detail) {
        String ipAddress = MDC.get("clientIp");
        String requestId = MDC.get("requestId");

        eventPublisher.publishEvent(new AuditEvent(
                action, actorUserId, actorAccountId,
                targetEntityType, targetEntityId, detail,
                ipAddress, requestId
        ));
    }

    public void audit(String action, UUID actorUserId, UUID actorAccountId) {
        audit(action, actorUserId, actorAccountId, null, null, null);
    }

    public void audit(String action, UUID actorUserId, UUID actorAccountId, String detail) {
        audit(action, actorUserId, actorAccountId, null, null, detail);
    }
}
