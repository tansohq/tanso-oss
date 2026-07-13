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

import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.Entitlement;
import com.tansoflow.tansocore.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EntitlementRepository extends JpaRepository<Entitlement, UUID> {

    Optional<Entitlement> getEntitlementByFeatureKeyAndCustomer(String featureKey, Customer customer);

    List<Entitlement> findByCustomerAndFeatureKeyAndRevokedAtIsNull(Customer customer, String featureKey);

    Optional<Entitlement> findFirstByCustomerAndFeatureKeyAndRevokedAtIsNullOrderByCreatedAtDesc(Customer customer, String featureKey);

    List<Entitlement> findAllByCustomerAndRevokedAtIsNull(Customer customer);

    List<Entitlement> findAllByCustomerAndSubscription(Customer customer, Subscription subscription);

    List<Entitlement> findAllBySubscription(Subscription subscription);

    @Query("SELECT e FROM Entitlement e WHERE e.customer IN :customers AND e.revokedAt IS NULL")
    List<Entitlement> findActiveEntitlementsByCustomerIn(@Param("customers") List<Customer> customers);

    /**
     * Remove plan-rule-derived entitlements for a given (account, plan, feature)
     * from all active subscriptions on that plan.
     */
    @Query("""
        SELECT e FROM Entitlement e
        WHERE e.id = :id
          AND e.customer.account.id = :accountId
          AND e.revokedAt IS NULL
    """)
    Optional<Entitlement> findByIdAndAccountId(@Param("id") UUID id, @Param("accountId") UUID accountId);

    @Modifying
    @Query(value = """
        DELETE FROM entitlements e
        USING subscriptions s, features f
        WHERE e.subscription_id = s.subscription_id
          AND s.account_id      = :accountId
          AND s.plan_id         = :planId
          AND s.is_active       = TRUE
          AND s.deleted_at IS NULL
          AND s.archived_at IS NULL
          AND f.feature_id      = :featureId
          AND e.feature_key     = f.key
          AND e.deleted_at IS NULL
          AND e.archived_at IS NULL
        """,
            nativeQuery = true)
    int deletePlanRuleEntitlements(@Param("accountId") UUID accountId,
                                   @Param("planId") UUID planId,
                                   @Param("featureId") UUID featureId);

    @Modifying
    @Query(value = """
    INSERT INTO entitlements (
        entitlement_id,
        feature_key,
        customer_id,
        subscription_id,
        entitlement_meta_id,
        created_at,
        modified_at
    )
    SELECT
        gen_random_uuid(),
        f.key,
        s.customer_id,
        s.subscription_id,
        :entitlementMetaId,
        now(),
        now()
    FROM subscriptions s
    JOIN features f
      ON f.feature_id = :featureId
    LEFT JOIN entitlements e
      ON e.subscription_id = s.subscription_id
     AND e.feature_key     = f.key
     AND e.deleted_at IS NULL
     AND e.archived_at IS NULL
    WHERE s.account_id = :accountId
      AND s.plan_id    = :planId
      AND s.is_active  = TRUE
      AND s.deleted_at IS NULL
      AND s.archived_at IS NULL
      AND e.entitlement_id IS NULL
    """, nativeQuery = true)
    int insertMissingPlanRuleEntitlements(UUID accountId,
                                          UUID planId,
                                          UUID featureId,
                                          UUID entitlementMetaId);
}
