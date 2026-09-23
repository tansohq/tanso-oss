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
package com.tansoflow.tansocore.model.signup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentSignupResponseJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // The runbook says null fields are written out, never omitted, and a null spend_cap means no per-charge limit.
    // The limits object dropped it when the operator had set no cap.
    @Test
    void limitsWriteOutANullSpendCap() throws Exception {
        AgentSignupResponse.AgentLimits limits = AgentSignupResponse.AgentLimits.builder()
                .features(Map.of())
                .currency("USD")
                .build();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(limits));

        assertThat(json.has("spend_cap")).isTrue();
        assertThat(json.get("spend_cap").isNull()).isTrue();
    }
}
