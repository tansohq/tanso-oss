package com.tansoflow.tansocore.model.customer.request;

import lombok.Data;

@Data
public class CustomerUpdateRequest {
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
}
