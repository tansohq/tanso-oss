/*
 * Tanso Core - open-source B2B SaaS monetization engine
 * Copyright (C) 2026  Douglas Baek
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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
