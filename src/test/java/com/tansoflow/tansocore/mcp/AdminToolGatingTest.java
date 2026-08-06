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

import com.tansoflow.tansocore.mcp.tools.AdminCreditTools;
import com.tansoflow.tansocore.mcp.tools.AdminEventTools;
import com.tansoflow.tansocore.mcp.tools.AdminFeatureTools;
import com.tansoflow.tansocore.mcp.tools.AdminPlanFeatureRuleTools;
import com.tansoflow.tansocore.mcp.tools.AdminPlanTools;
import com.tansoflow.tansocore.mcp.tools.StripeSetupTools;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Admin MCP tools expose tenant configuration (plans, tariffs, Stripe setup),
 * so their beans must require the explicit admin-tools opt-in on top of
 * app.mcp.enabled. A class losing this gate reopens the privilege-escalation
 * path where any client API key can reconfigure the tenant over MCP.
 */
class AdminToolGatingTest {

    @ParameterizedTest
    @ValueSource(classes = {
            AdminCreditTools.class, AdminEventTools.class, AdminFeatureTools.class,
            AdminPlanFeatureRuleTools.class, AdminPlanTools.class, StripeSetupTools.class})
    void adminToolClassesRequireBothFlags(Class<?> toolClass) {
        ConditionalOnProperty condition = toolClass.getAnnotation(ConditionalOnProperty.class);
        assertThat(condition).as("%s must be @ConditionalOnProperty-gated", toolClass.getSimpleName()).isNotNull();
        assertThat(condition.name()).contains("app.mcp.enabled", "app.mcp.admin-tools.enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(condition.matchIfMissing()).isFalse();
    }
}
