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
package com.tansoflow.tansocore.model.credit;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Schema(description = "Data Transfer Object for Plan Credit Allocation")
public class PlanCreditAllocationDto {
    @Schema(description = "Unique identifier of the allocation")
    private String id;

    @Schema(description = "Credit model ID")
    private String creditModelId;

    @Schema(description = "Credit model name")
    private String creditModelName;

    @Schema(description = "Credit denomination")
    private String denomination;

    @Schema(description = "Number of credits allocated per period")
    private BigDecimal creditAmount;

    @Schema(description = "Number of months before granted credits expire")
    private Integer grantExpiresMonths;

    @Schema(description = "Whether to block events when credits are depleted (true) or allow overage (false). Null inherits from credit model.")
    private Boolean hardLimit;

    @Schema(description = "Timestamp when the allocation was created")
    private Instant createdAt;
}
