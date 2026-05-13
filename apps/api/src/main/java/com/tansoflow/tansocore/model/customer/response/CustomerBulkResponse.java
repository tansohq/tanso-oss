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
