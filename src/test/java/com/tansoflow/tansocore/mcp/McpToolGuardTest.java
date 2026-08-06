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
package com.tansoflow.tansocore.mcp;

import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.mcp.config.McpToolGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpToolGuardTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String accountId) {
        UserContext ctx = new UserContext(accountId, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(ctx, null, List.of()));
    }

    @Test
    void deniesWhenAdminToolsDisabled() {
        McpToolGuard guard = new McpToolGuard(false);
        authenticate(UUID.randomUUID().toString());
        assertThatThrownBy(guard::requireAdminAccountId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("admin_tools_disabled");
    }

    @Test
    void deniesWhenSecurityContextEmpty() {
        McpToolGuard guard = new McpToolGuard(true);
        assertThatThrownBy(guard::requireAdminAccountId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unauthorized");
    }

    @Test
    void deniesWhenPrincipalIsNotUserContext() {
        McpToolGuard guard = new McpToolGuard(true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymous", null, List.of()));
        assertThatThrownBy(guard::requireAdminAccountId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unauthorized");
    }

    @Test
    void returnsAccountIdWhenEnabledAndAuthenticated() {
        McpToolGuard guard = new McpToolGuard(true);
        String accountId = UUID.randomUUID().toString();
        authenticate(accountId);
        assertThat(guard.requireAdminAccountId()).isEqualTo(accountId);
    }
}
