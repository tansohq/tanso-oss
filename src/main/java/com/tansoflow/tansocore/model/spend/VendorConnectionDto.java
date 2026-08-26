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
package com.tansoflow.tansocore.model.spend;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tansoflow.tansocore.model.spend.type.VendorConnectionStatus;
import com.tansoflow.tansocore.model.spend.type.VendorProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VendorConnectionDto {
    private String id;
    private VendorProvider provider;
    private String label;
    @Schema(description = "Last four characters of the admin key. The key itself is never returned.")
    private String keyHint;
    @Schema(description = "Provider scope: the GitHub organization for Copilot, the proxy URL for LiteLLM. Null for providers that have none.")
    private String scope;
    private VendorConnectionStatus status;
    @Schema(description = "Why the last probe or sync failed; null while healthy.")
    private String lastError;
    private Instant lastSyncedAt;
    private Instant createdAt;
}
