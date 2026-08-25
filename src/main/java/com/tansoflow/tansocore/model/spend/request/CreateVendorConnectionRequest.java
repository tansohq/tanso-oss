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
package com.tansoflow.tansocore.model.spend.request;

import com.tansoflow.tansocore.model.spend.type.VendorProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

@Data
public class CreateVendorConnectionRequest {
    @NotNull
    private VendorProvider provider;

    @NotBlank
    @Size(max = 100)
    @Schema(description = "How this org shows up in the console, e.g. \"Anthropic — engineering org\"")
    private String label;

    @NotBlank
    @ToString.Exclude
    @Schema(description = "Vendor admin key. Stored encrypted; never returned.")
    private String adminKey;
}
