package com.tansoflow.tansocore.model.customer.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tansoflow.tansocore.model.credit.CreditPoolDto;
import com.tansoflow.tansocore.model.subscription.SubscriptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Response containing customer details and their subscriptions")
public class CustomerClientResponse {
    @Schema(description = "External client reference ID for the customer", example = "cust_12345")
    private String customerReferenceId;

    @Schema(description = "First name of the customer", example = "John")
    private String firstName;

    @Schema(description = "Last name of the customer", example = "Doe")
    private String lastName;

    @Schema(description = "Email address of the customer", example = "john.doe@example.com")
    private String email;

    @Schema(description = "Timestamp when the customer record was created")
    private String createdAt;

    @Schema(description = "Timestamp when the customer record was last modified")
    private String modifiedAt;

    @Schema(description = "List of subscriptions associated with the customer")
    private List<SubscriptionDto> subscriptions;

    @Schema(description = "Credit pools associated with the customer")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<CreditPoolDto> creditPools;
}
