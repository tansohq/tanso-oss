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
package com.tansoflow.tansocore.model.customer;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Data Transfer Object for Customer information")
public class CustomerDto {
    @Schema(description = "Unique identifier of the customer")
    private String id;

    @Schema(description = "External client reference ID for the customer", example = "cust_12345")
    private String customerReferenceId;

    @Schema(description = "First name of the customer", example = "John")
    private String firstName;

    @Schema(description = "Last name of the customer", example = "Doe")
    private String lastName;

    @Schema(description = "Email address of the customer", example = "john.doe@example.com")
    private String email;

    @Schema(description = "Phone number of the customer", example = "+1234567890")
    private String phoneNumber;

    @Schema(description = "Source of customer creation: MANUAL, EVENT_AUTO_CREATED, or STRIPE_IMPORTED")
    private String source;

    @Schema(description = "Timestamp when the customer record was created")
    private String createdAt;

    @Schema(description = "Timestamp when the customer record was last modified")
    private String modifiedAt;
}
