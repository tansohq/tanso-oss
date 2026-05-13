package com.tansoflow.tansocore.model.customer.request;

import lombok.Data;

import java.util.List;

@Data
public class CustomerBulkRequest {
    private List<CustomerRequestElement> customers;

    @Data
    public static class CustomerRequestElement {
        private String id;
        private String referenceId;
        private String firstName;
        private String lastName;
        private String email;
        private String phoneNumber;
        private String address;
    }
}
