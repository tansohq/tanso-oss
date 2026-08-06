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
package com.tansoflow.tansocore.mcp.config;

import com.tansoflow.tansocore.auth.UserContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Fail-closed access checks for MCP tools. Admin tool beans only exist when
 * app.mcp.admin-tools.enabled is true, but every admin tool method still
 * resolves its account through this guard so a wiring mistake denies instead
 * of exposing tenant configuration.
 */
@Component
@ConditionalOnProperty(name = "app.mcp.enabled", havingValue = "true")
public class McpToolGuard {

    private final boolean adminToolsEnabled;

    public McpToolGuard(@Value("${app.mcp.admin-tools.enabled:false}") boolean adminToolsEnabled) {
        this.adminToolsEnabled = adminToolsEnabled;
    }

    public String requireAdminAccountId() {
        if (!adminToolsEnabled) {
            throw new IllegalStateException(
                    "admin_tools_disabled: MCP admin tools are disabled on this instance (app.mcp.admin-tools.enabled)");
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserContext ctx)
                || ctx.getAccountId() == null) {
            throw new IllegalStateException("unauthorized: no authenticated account in context");
        }
        return ctx.getAccountId();
    }
}
