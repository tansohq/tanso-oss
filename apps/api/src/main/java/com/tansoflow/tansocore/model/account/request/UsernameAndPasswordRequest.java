package com.tansoflow.tansocore.model.account.request;

import lombok.Data;

@Data
public class UsernameAndPasswordRequest {
    private String username;
    private String password;
}
