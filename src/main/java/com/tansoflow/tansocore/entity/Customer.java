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
package com.tansoflow.tansocore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import com.tansoflow.tansocore.model.customer.CustomerSource;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "customers")
@SQLRestriction("deleted_at IS NULL")
@SQLDelete(sql = "UPDATE customers SET deleted_at = now() WHERE customer_id = ?")
public class Customer {
    @Id
    @GeneratedValue
    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID id;

    @Setter(AccessLevel.NONE)
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Setter(AccessLevel.NONE)
    @Column(name = "modified_at", insertable = false)
    @UpdateTimestamp
    private Instant modifiedAt;

    @Size(max = 100)
    @Column(name = "first_name", length = 100)
    private String firstName;

    @Size(max = 100)
    @Column(name = "last_name", length = 100)
    private String lastName;

    @Size(max = 255)
    @Column(name = "email")
    private String email;

    @Size(max = 20)
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "account_id", updatable = false, nullable = false)
    private Account account;

    @Size(max = 255)
    @Column(name = "external_client_customer_id")
    private String externalClientCustomerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id")
    private Address address;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 32)
    private CustomerSource source;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

}