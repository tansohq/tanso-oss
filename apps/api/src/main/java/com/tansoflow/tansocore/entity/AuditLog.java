package com.tansoflow.tansocore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "audit_log")
public class AuditLog {
    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_account_id")
    private UUID actorAccountId;

    @Column(name = "target_entity_type", length = 100)
    private String targetEntityType;

    @Column(name = "target_entity_id", length = 255)
    private String targetEntityId;

    @Column(name = "detail", columnDefinition = "text")
    private String detail;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "request_id", length = 36)
    private String requestId;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;
}
