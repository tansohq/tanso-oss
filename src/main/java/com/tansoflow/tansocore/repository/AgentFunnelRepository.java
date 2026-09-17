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

import com.tansoflow.tansocore.model.analytics.AgentFunnelSignupRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Native SQL on purpose: the agent_* columns are added by a parallel changelog and this
// report must not depend on the Customer entity carrying them.
@Repository
public class AgentFunnelRepository {

    private static final String SIGNUPS_SQL = """
            SELECT c.created_at,
                   c.agent_status,
                   c.agent_claimed_at,
                   (SELECT MIN(e.occurred_at)
                      FROM events e
                     WHERE e.customer_id = c.customer_id) AS first_event_at,
                   LEAST(
                       (SELECT MIN(i.modified_at)
                          FROM invoices i
                          JOIN subscriptions s ON s.subscription_id = i.subscription_id
                         WHERE s.customer_id = c.customer_id
                           AND i.status IN ('PAID', 'ADJUSTMENT_PAID')
                           AND i.deleted_at IS NULL),
                       (SELECT MIN(cs.completed_at)
                          FROM checkout_sessions cs
                         WHERE cs.customer_id = c.customer_id
                           AND cs.status = 'COMPLETED')
                   ) AS first_paid_at
              FROM customers c
             WHERE c.account_id = :accountId
               AND c.external_client_customer_id LIKE 'agent\\_%'
               AND c.created_at >= :from
               AND c.created_at < :to
               AND c.deleted_at IS NULL
             ORDER BY c.created_at
            """;

    @PersistenceContext
    private EntityManager entityManager;

    public List<AgentFunnelSignupRow> findAgentSignups(UUID accountId, Instant from, Instant to) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(SIGNUPS_SQL)
                .setParameter("accountId", accountId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
        List<AgentFunnelSignupRow> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            result.add(new AgentFunnelSignupRow(
                    toInstant(row[0]),
                    (String) row[1],
                    toInstant(row[2]),
                    toInstant(row[3]),
                    toInstant(row[4])));
        }
        return result;
    }

    // Postgres timestamp comes back as Timestamp, timestamptz as OffsetDateTime; the columns mix both.
    private static Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        throw new IllegalStateException("Unexpected timestamp type from agent funnel query: " + value.getClass());
    }
}
