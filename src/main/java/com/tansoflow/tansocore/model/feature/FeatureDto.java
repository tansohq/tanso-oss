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
package com.tansoflow.tansocore.model.feature;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Data Transfer Object for Feature information")
public class FeatureDto {
    @Schema(description = "Unique identifier of the feature")
    private UUID id;

    @Schema(description = "Display name of the feature", example = "SSO Authentication")
    private String name;

    @Schema(description = "Unique key for the feature", example = "sso_auth")
    private String key;

    @Schema(description = "Detailed description of the feature")
    private String description;

    @Schema(description = "Timestamp when the feature was created")
    private Instant createdAt;

    @Schema(description = "Timestamp when the feature was last modified")
    private Instant modifiedAt;

    @Schema(description = "Status indicating if the feature is enabled")
    private Boolean isEnabled;

    @Schema(description = "Additional metadata associated with the feature")
    private Map<String, Object> metadata;
}
