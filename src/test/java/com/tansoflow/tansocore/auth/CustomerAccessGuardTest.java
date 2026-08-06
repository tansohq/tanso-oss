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
package com.tansoflow.tansocore.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerAccessGuardTest {

    private final CustomerAccessGuard guard = new CustomerAccessGuard();
    private final String accountId = UUID.randomUUID().toString();

    private UserContext tenantKey() {
        return new UserContext(accountId, null);
    }

    private UserContext customerKey(String ref, List<String> scopes) {
        return new UserContext(accountId, UUID.randomUUID().toString(), ref, scopes, null);
    }

    @Test
    void tenantKeyPassesThroughAnyRef() {
        assertThat(guard.resolveCustomerRef(tenantKey(), "cust-1")).isEqualTo("cust-1");
        assertThat(guard.resolveCustomerRef(tenantKey(), null)).isNull();
    }

    @Test
    void customerKeyResolvesOwnRef() {
        UserContext ctx = customerKey("cust-1", List.of("read"));
        assertThat(guard.resolveCustomerRef(ctx, "cust-1")).isEqualTo("cust-1");
        assertThat(guard.resolveCustomerRef(ctx, null)).isEqualTo("cust-1");
    }

    @Test
    void customerKeyIsDeniedAnotherCustomersRef() {
        UserContext ctx = customerKey("cust-1", List.of("read"));
        assertThatThrownBy(() -> guard.resolveCustomerRef(ctx, "cust-2"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void purchaseScopeIsEnforcedForCustomerKeysOnly() {
        guard.requirePurchaseScope(tenantKey());
        guard.requirePurchaseScope(customerKey("cust-1", List.of("read", "purchase")));
        assertThatThrownBy(() -> guard.requirePurchaseScope(customerKey("cust-1", List.of("read"))))
                .isInstanceOf(AccessDeniedException.class);
    }
}
