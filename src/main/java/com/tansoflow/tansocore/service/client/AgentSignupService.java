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

public interface AgentSignupService {

    /**
     * One-call agent onboarding for an account that opted in: creates a
     * Customer with a generated reference ID, subscribes it to the account's
     * free default plan, and issues a customer-scoped API key. Fails closed:
     * 404 when the slug is unknown, signup is disabled, or the default plan
     * is missing; RateLimitExceededException when the hourly cap is hit.
     */
    AgentSignupResponse signup(String slug, AgentSignupRequest request, String baseUrl);
}
