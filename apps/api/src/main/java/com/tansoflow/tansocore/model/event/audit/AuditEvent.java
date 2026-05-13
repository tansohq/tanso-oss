package com.tansoflow.tansocore.model.event.audit;

import java.util.UUID;

public record AuditEvent(
        String action,
        UUID actorUserId,
        UUID actorAccountId,
        String targetEntityType,
        String targetEntityId,
        String detail,
        String ipAddress,
        String requestId
) {
}
