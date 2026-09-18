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

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Pins customer-scoped (ck_) callers to their own customer. Tenant (sk_)
 * callers pass through untouched. Call at the top of every client endpoint
 * opened to ROLE_CUSTOMER.
 */
@Component
public class CustomerAccessGuard {

    /** The key is valid but belongs to a different customer than the one in the URL or body. */
    public static class OtherCustomerException extends AccessDeniedException {
        public OtherCustomerException(String message) {
            super(message);
        }
    }

    /** The key is valid but lacks a scope the endpoint needs. */
    public static class MissingScopeException extends AccessDeniedException {
        public MissingScopeException(String message) {
            super(message);
        }
    }

    /**
     * Returns the customer reference the request is allowed to act on: the
     * requested one for tenant keys, the key's own for customer keys (403 on
     * mismatch). A null requestedRef from a customer key resolves to its own.
     */
    public String resolveCustomerRef(UserContext ctx, String requestedRef) {
        if (!ctx.isCustomerScoped()) {
            return requestedRef;
        }
        String own = ctx.getCustomerReferenceId();
        if (requestedRef == null || requestedRef.equals(own)) {
            return own;
        }
        throw new OtherCustomerException("This API key is scoped to another customer");
    }

    public void requirePurchaseScope(UserContext ctx) {
        if (ctx.isCustomerScoped()
                && (ctx.getScopes() == null || !ctx.getScopes().contains("purchase"))) {
            throw new MissingScopeException("This API key lacks the 'purchase' scope");
        }
    }
}
