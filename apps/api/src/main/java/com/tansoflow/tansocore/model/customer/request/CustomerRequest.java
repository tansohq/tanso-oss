package com.tansoflow.tansocore.model.customer.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CustomerRequest {
    private String customerReferenceId;
    private String firstName;
    private String lastName;
    @NotBlank(message = "Email is required")
    private String email;
    private String phoneNumber;
    private String address;
}
