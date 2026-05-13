package com.tansoflow.tansocore.model.account.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EnvironmentTokenResponse {
    private String token;
    private String type;
    private String apiBaseUrl;
}
