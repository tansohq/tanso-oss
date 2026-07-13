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
package com.tansoflow.tansocore.model.billing;

import com.tansoflow.tansocore.model.subscription.SubscriptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Schema(description = "Data Transfer Object for Invoice information")
public class InvoiceDto {
    @Schema(description = "Timestamp when the invoice was created")
    private Instant createdAt;

    @Schema(description = "Timestamp when the invoice was last modified")
    private Instant modifiedAt;

    @Schema(description = "Unique identifier of the invoice")
    private String id;

    @Schema(description = "Total amount of the invoice", example = "29.99")
    private BigDecimal amount;

    @Schema(description = "Due date of the invoice")
    private Instant dueDate;

    @Schema(description = "Currency of the invoice", example = "USD")
    private String currency;

    @Schema(description = "Associated subscription details")
    private SubscriptionDto subscription;

    @Schema(description = "Current status of the invoice", example = "PAID")
    private String status;

    @Schema(description = "Additional metadata associated with the invoice")
    private Map<String, Object> metadata;

    @Schema(description = "Line items for this invoice (only populated on detail endpoint)")
    private List<InvoiceItemDto> items;

}
