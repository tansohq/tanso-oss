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
package com.tansoflow.tansocore.service.client;

import com.tansoflow.tansocore.model.signup.AgentSignupResponse;
import com.tansoflow.tansocore.model.signup.request.AgentSignupRequest;

import java.util.UUID;

public interface AgentSignupService {

    /**
     * One-call agent onboarding for an account that opted in: creates a
     * PROVISIONAL Customer with a generated reference ID and an expiry,
     * subscribes it to the account's free default plan, and issues a
     * customer-scoped API key. No email needed; the first payment claims it.
     * Fails closed: 404 when the slug is unknown, signup is disabled, or the
     * default plan is missing; RateLimitExceededException when the per-account
     * or per-IP hourly cap is hit.
     */
    AgentSignupResponse signup(String slug, AgentSignupRequest request, String baseUrl, String clientIp);

    /**
     * Opens a new spend mandate page for an existing customer, for a first mandate after signup or a
     * higher one. Same checks as signup: currency must match and max_amount must not be above the
     * operator's agentMaxMandateAmount. When the principal completes it, it replaces the customer's
     * current mandate. Status is {@code unavailable} when mandates are off, Stripe is not connected or
     * Stripe failed to open a page.
     */
    AgentSignupResponse.AgentSpendMandate requestSpendMandate(String accountId, String customerReferenceId,
                                                              UUID apiKeyId,
                                                              AgentSignupRequest.SpendMandate requested);
}
