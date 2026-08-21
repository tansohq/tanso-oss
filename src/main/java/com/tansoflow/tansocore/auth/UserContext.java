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

import java.util.List;

import lombok.Data;

@Data
public class UserContext {
    private String userId;
    private String accountId;
    private String email;
    private String apiKey;
    // Set only for customer-scoped (ck_) API keys
    private String customerId;
    private String customerReferenceId;
    private List<String> scopes;
    // Row id of the authenticating key. Set only for ck_ keys — it is the actor
    // identity a per-key budget is enforced against. Null for JWT/UI traffic.
    private java.util.UUID apiKeyId;

    public UserContext(String accountId, String apiKey) {
        this.accountId = accountId;
        this.apiKey = apiKey;
    }

    public UserContext(String accountId, String customerId, String customerReferenceId, List<String> scopes, String apiKey) {
        this(accountId, customerId, customerReferenceId, scopes, apiKey, null);
    }

    public UserContext(String accountId, String customerId, String customerReferenceId, List<String> scopes,
                       String apiKey, java.util.UUID apiKeyId) {
        this.apiKeyId = apiKeyId;
        this.accountId = accountId;
        this.customerId = customerId;
        this.customerReferenceId = customerReferenceId;
        this.scopes = scopes;
        this.apiKey = apiKey;
    }

    public boolean isCustomerScoped() {
        return customerId != null;
    }

    public UserContext(String userId, String accountId, String email, String apiKey) {
        this.userId = userId;
        this.accountId = accountId;
        this.email = email;
        this.apiKey = apiKey;
    }
}