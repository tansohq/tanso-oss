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
package com.tansoflow.tansocore.repository;

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> getCustomerById(UUID id);

    List<Customer> getCustomersByAccount(Account account);

    @Query("SELECT c FROM Customer c WHERE c.externalClientCustomerId = :referenceId AND c.account.id = :accountId")
    Optional<Customer> getCustomerByReferenceIdAndAccountId(String referenceId, UUID accountId);

    @Query("SELECT COUNT(c) > 0 FROM Customer c WHERE c.id = :id AND c.account.id = :accountId")
    boolean existsByIdAndAccountId(UUID id, UUID accountId);

    @Query("SELECT COUNT(c) FROM Customer c WHERE c.account.id = :accountId "
            + "AND c.externalClientCustomerId LIKE 'agent\\_%' ESCAPE '\\' AND c.createdAt >= :since")
    long countAgentSignupsSince(UUID accountId, java.time.Instant since);

    @Query("SELECT COUNT(c) FROM Customer c WHERE c.agentSignupIp = :ip "
            + "AND c.externalClientCustomerId LIKE 'agent\\_%' ESCAPE '\\' AND c.createdAt >= :since")
    long countAgentSignupsFromIpSince(String ip, java.time.Instant since);

    @Query("SELECT c FROM Customer c WHERE c.agentStatus = com.tansoflow.tansocore.entity.AgentStatus.PROVISIONAL "
            + "AND c.agentExpiresAt < :now")
    List<Customer> findExpiredProvisionalAgentCustomers(java.time.Instant now);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Customer c SET c.agentStatus = com.tansoflow.tansocore.entity.AgentStatus.EXPIRED "
            + "WHERE c.id = :id AND c.agentStatus = com.tansoflow.tansocore.entity.AgentStatus.PROVISIONAL "
            + "AND c.agentExpiresAt < :now")
    int expireIfStillProvisional(UUID id, java.time.Instant now);
}
