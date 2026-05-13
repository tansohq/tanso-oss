package com.tansoflow.tansocore.auth;

import lombok.Data;

@Data
public class UserContext {
    private String userId;
    private String accountId;
    private String email;
    private String apiKey;

    public UserContext(String accountId, String apiKey) {
        this.accountId = accountId;
        this.apiKey = apiKey;
    }

    public UserContext(String userId, String accountId, String email, String apiKey) {
        this.userId = userId;
        this.accountId = accountId;
        this.email = email;
        this.apiKey = apiKey;
    }
}