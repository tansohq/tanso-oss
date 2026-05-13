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
