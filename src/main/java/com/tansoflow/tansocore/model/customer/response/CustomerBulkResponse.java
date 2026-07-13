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
package com.tansoflow.tansocore.model.customer.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Response containing a list of customers")
public class CustomerBulkResponse {
    @Schema(description = "List of simplified customer objects")
    List<CustomerElement> customers;

    @Data
    @Schema(description = "Simplified customer information for bulk display")
    public static class CustomerElement {
        @Schema(description = "Internal unique identifier", example = "550e8400-e29b-41d4-a716-446655440000")
        private String id;

        @Schema(description = "External client reference ID", example = "cust_001")
        private String referenceId;

        @Schema(description = "First name of the customer", example = "John")
        private String firstName;

        @Schema(description = "Last name of the customer", example = "Doe")
        private String lastName;

        @Schema(description = "Email address of the customer", example = "john.doe@example.com")
        private String email;

        @Schema(description = "Timestamp when the customer was created")
        private String createdAt;
    }
}
